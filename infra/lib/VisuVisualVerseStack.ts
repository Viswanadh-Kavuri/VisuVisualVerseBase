import * as cdk from 'aws-cdk-lib/core';
import { Construct } from 'constructs';
// import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as apigw from 'aws-cdk-lib/aws-apigateway';
import * as acm from 'aws-cdk-lib/aws-certificatemanager';
import * as targets from 'aws-cdk-lib/aws-route53-targets';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as s3deploy from 'aws-cdk-lib/aws-s3-deployment';
import * as path from 'path';

export class VisuVisualVerseStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);
    const ROOT_DOMAIN = 'visuvisualverse.com';
    const API_SUBDOMAIN = `api.${ROOT_DOMAIN}`;

    //hostedzone

    const hostedZone =  route53.HostedZone.fromLookup(this, 'HostedZone', {
      domainName: ROOT_DOMAIN,
    });

    const certificate = new acm.Certificate(this, 'SiteCertificate', {
      domainName: ROOT_DOMAIN,
      subjectAlternativeNames: [API_SUBDOMAIN],
      validation: acm.CertificateValidation.fromDns(hostedZone),
    });

    // 3. DynamoDB table
    const todoTable = new dynamodb.Table(this, 'TodosTable', {
      partitionKey: { name: 'id', type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: cdk.RemovalPolicy.DESTROY, // NOT recommended for production code
    });

    const todoLambda = new lambda.Function(this, 'TodoLambda', {
      runtime: lambda.Runtime.JAVA_17,
      handler: 'com.visu.todo.TodoLambdaHandler::handleRequest', 
      code: lambda.Code.fromAsset(
        path.join(__dirname, '../../lambda/target/todo-lambda-1.0.0.jar')
      ),
      memorySize: 512,
      timeout: cdk.Duration.seconds(10),
      environment: {
        TODO_TABLE: todoTable.tableName,
      },
    });

    todoTable.grantReadWriteData(todoLambda);

    // 5. API Gateway REST API with automatic CORS/OPTIONS
    const api = new apigw.LambdaRestApi(this, 'TodoApi', {
      handler: todoLambda,
      proxy: false,
      restApiName: 'TodoService',
      deployOptions: {
        stageName: 'prod',
      },
      // This is the key: API Gateway will create OPTIONS and respond with CORS headers
      defaultCorsPreflightOptions: {
        allowOrigins: apigw.Cors.ALL_ORIGINS,
        allowMethods: ['GET', 'POST', 'DELETE', 'OPTIONS'],
        allowHeaders: ['Content-Type'],
      },
    });

    // Resources: /todos and /todos/{id}
    const todosResource = api.root.addResource('todos');
    todosResource.addMethod('GET');   // GET /todos
    todosResource.addMethod('POST');  // POST /todos

    const todoIdResource = todosResource.addResource('{id}');
    todoIdResource.addMethod('DELETE'); // DELETE /todos/{id}

    // 6. Custom domain for API: api.visuvisualverse.com
    const apiDomainName = new apigw.DomainName(this, 'ApiCustomDomain', {
      domainName: API_SUBDOMAIN,
      certificate,
      endpointType: apigw.EndpointType.REGIONAL,
      securityPolicy: apigw.SecurityPolicy.TLS_1_2,
    });

    new apigw.BasePathMapping(this, 'ApiBasePathMapping', {
      domainName: apiDomainName,
      restApi: api,
      basePath: '',
    });

    new route53.ARecord(this, 'ApiAliasRecord', {
      zone: hostedZone,
      recordName: API_SUBDOMAIN,
      target: route53.RecordTarget.fromAlias(
        new targets.ApiGatewayDomain(apiDomainName)
      ),
    });

    // 7. S3 bucket for React app
    const siteBucket = new s3.Bucket(this, 'SiteBucket', {
      publicReadAccess: false,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });

    // 8. CloudFront for visuvisualverse.com
    const distribution = new cloudfront.Distribution(this, 'SiteDistribution', {
      defaultRootObject: 'index.html',
      domainNames: [ROOT_DOMAIN],
      certificate,
      defaultBehavior: {
        origin: new origins.S3Origin(siteBucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
      },
      errorResponses: [
        {
          httpStatus: 404,
          responseHttpStatus: 200,
          responsePagePath: '/index.html',
          ttl: cdk.Duration.minutes(5),
        },
      ],
    });

    new route53.ARecord(this, 'SiteAliasRecord', {
      zone: hostedZone,
      recordName: ROOT_DOMAIN,
      target: route53.RecordTarget.fromAlias(
        new targets.CloudFrontTarget(distribution)
      ),
    });

    // 9. Deploy React build to S3
    new s3deploy.BucketDeployment(this, 'DeployWebsite', {
      sources: [
        s3deploy.Source.asset(
          path.join(__dirname, '../../frontend/build') // CRA; change to dist for Vite
        ),
      ],
      destinationBucket: siteBucket,
      distribution,
      distributionPaths: ['/*'],
    });

    // 10. Outputs
    new cdk.CfnOutput(this, 'FrontendUrl', {
      value: `https://${ROOT_DOMAIN}`,
      description: 'URL of the React frontend',
    });

    new cdk.CfnOutput(this, 'ApiBaseUrl', {
      value: `https://${API_SUBDOMAIN}`,
      description: 'Base URL of the API',
    });
    
  }
}

package com.visu.todo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lambda handler for API Gateway REST API (proxy=false).
 *
 * Routes:
 *   GET    /todos
 *   POST   /todos
 *   DELETE /todos/{id}
 *
 * Uses DynamoDB Enhanced Client and the ToDoItem @DynamoDbBean.
 */
public class TodoLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TODO_TABLE_ENV = "TODO_TABLE";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DynamoDbTable<ToDoItem> todoTable;

    public TodoLambdaHandler() {
        // Read table name from environment
        String tableName = Optional.ofNullable(System.getenv(TODO_TABLE_ENV))
                .orElseThrow(() -> new IllegalStateException("Environment variable TODO_TABLE is not set"));

        // Use Lambda's region
        String region = Optional.ofNullable(System.getenv("AWS_REGION")).orElse("us-east-1");

        DynamoDbClient lowLevelClient = DynamoDbClient.builder()
                .region(Region.of(region))
                .build();

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(lowLevelClient)
                .build();

        // IMPORTANT: this will fail at init if ToDoItem mapping is wrong
        this.todoTable = enhancedClient.table(tableName, TableSchema.fromBean(ToDoItem.class));
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String method = event.getHttpMethod();         // e.g. "GET"
            String resource = event.getResource();         // e.g. "/todos", "/todos/{id}"

            context.getLogger().log("Received request: " + method + " " + resource);

            if ("GET".equalsIgnoreCase(method) && "/todos".equals(resource)) {
                return handleGetTodos();

            } else if ("POST".equalsIgnoreCase(method) && "/todos".equals(resource)) {
                return handleCreateTodo(event.getBody());

            } else if ("DELETE".equalsIgnoreCase(method) && "/todos/{id}".equals(resource)) {
                String id = null;
                if (event.getPathParameters() != null) {
                    id = event.getPathParameters().get("id");
                }
                return handleDeleteTodo(id);
            }

            return jsonResponse(404, Map.of("message", "Not found"));
        } catch (Exception e) {
            context.getLogger().log("Error handling request: " + e.getMessage());
            return jsonResponse(500, Map.of("message", "Internal server error"));
        }
    }

    // ----- Handlers for each route -----

    private APIGatewayProxyResponseEvent handleGetTodos() throws Exception {
        PageIterable<ToDoItem> pages = todoTable.scan();
        List<ToDoItem> todos = pages.items().stream().collect(Collectors.toList());
        return jsonResponse(200, todos);
    }

    private APIGatewayProxyResponseEvent handleCreateTodo(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return jsonResponse(400, Map.of("message", "Request body is required"));
        }

        Map<String, Object> input = MAPPER.readValue(body, Map.class);
        String title = Optional.ofNullable(input.get("title"))
                .map(Object::toString)
                .map(String::trim)
                .orElse("");

        if (title.isEmpty()) {
            return jsonResponse(400, Map.of("message", "Field 'title' is required"));
        }

        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();

        ToDoItem todo = new ToDoItem(id, title, createdAt, false);
        todoTable.putItem(todo);

        return jsonResponse(201, todo);
    }

    private APIGatewayProxyResponseEvent handleDeleteTodo(String id) throws Exception {
        if (id == null || id.isBlank()) {
            return jsonResponse(400, Map.of("message", "Invalid id"));
        }

        todoTable.deleteItem(Key.builder().partitionValue(id).build());

        APIGatewayProxyResponseEvent resp = new APIGatewayProxyResponseEvent();
        resp.setStatusCode(204);
        resp.setHeaders(commonHeaders());
        resp.setBody("");
        return resp;
    }

    // ----- Response helpers -----

    private Map<String, String> commonHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "https://visuvisualverse.com");
        headers.put("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type");
        return headers;
    }

    private APIGatewayProxyResponseEvent jsonResponse(int statusCode, Object bodyObj) {
        APIGatewayProxyResponseEvent resp = new APIGatewayProxyResponseEvent();
        resp.setStatusCode(statusCode);
        resp.setHeaders(commonHeaders());

        try {
            String body = bodyObj == null ? "" : MAPPER.writeValueAsString(bodyObj);
            resp.setBody(body);
        } catch (Exception e) {
            resp.setStatusCode(500);
            resp.setBody("{\"message\":\"serialization error\"}");
        }

        return resp;
    }
}

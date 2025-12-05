#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib/core';
import { VisuVisualVerseStack } from '../lib/VisuVisualVerseStack';

const app = new cdk.App();
new VisuVisualVerseStack(app, 'VisuVisualVerseStack', {
  env: { account: '122441749594', region: 'us-east-1' },
});

package com.visu.todo;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;

public class TodoLambdaHandler implements com.amazonaws.services.lambda.runtime.RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

private static final String TODO_TABLE_ENV = "TODO_TABLE";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<ToDoItem> todoTable;

    public TodoLambdaHandler() {
        String tableName = Optional.ofNullable(System.getenv(TODO_TABLE_ENV))
                .orElseThrow(() -> new IllegalStateException("Environment variable TODO_TABLE is not set"));

        String region = Optional.ofNullable(System.getenv("AWS_REGION")).orElse("us-east-1");

        this.enhancedClient = DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(DynamoDbClient.builder()
                    .region(Region.of(region))
                    .build())
                .build();

        this.todoTable = enhancedClient.table(tableName, TableSchema.fromBean(ToDoItem.class));
    }


    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        context.getLogger().log("Input: " + event);

        try {
            String method = event.getRequestContext().getHttp().getMethod();
            String path = event.getRequestContext().getHttp().getPath();  

                    if ("GET".equalsIgnoreCase(method) && "/todos".equals(path)) {
                // GET /todos
                return handleGetTodos();

            } else if ("POST".equalsIgnoreCase(method) && "/todos".equals(path)) {
                // POST /todos
                return handleCreateTodo(event.getBody());

            } else if ("DELETE".equalsIgnoreCase(method) && path.startsWith("/todos/")) {
                // DELETE /todos/{id}
                String id = path.substring("/todos/".length());
                return handleDeleteTodo(id);

            } else if ("OPTIONS".equalsIgnoreCase(method)) {
                // CORS preflight support
                return APIGatewayV2HTTPResponse.builder()
                        .withStatusCode(204)
                        .withHeaders(commonHeaders())
                        .build();
            }

            // If we reach here, no route matched
            return jsonResponse(404, Map.of("message", "Not found"));

        } catch (Exception e) {
            context.getLogger().log("Error handling request: " + e.getMessage());
            return jsonResponse(500, Map.of("message", "Internal server error"));
        }

    }

        private APIGatewayV2HTTPResponse handleDeleteTodo(String id) throws Exception {
        if (id == null || id.isBlank()) {
            return jsonResponse(400, Map.of("message", "Invalid id"));
        }

        todoTable.deleteItem(Key.builder().partitionValue(id).build());

        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(204)
                .withHeaders(commonHeaders())
                .build();
    }

    private APIGatewayV2HTTPResponse handleGetTodos() throws Exception {
        PageIterable<ToDoItem> pages = todoTable.scan();
        List<ToDoItem> todos = pages.items().stream().collect(Collectors.toList());
        return jsonResponse(200, todos);
    }

    // POST /todos
    private APIGatewayV2HTTPResponse handleCreateTodo(String body) throws Exception {
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

    private Map<String, String> commonHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type");
        return headers;
    }

    private APIGatewayV2HTTPResponse jsonResponse(int statusCode, Object bodyObj) {
        try {
            String body = bodyObj == null ? "" : MAPPER.writeValueAsString(bodyObj);
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withHeaders(commonHeaders())
                    .withBody(body)
                    .build();
        } catch (Exception e) {
            // Fallback in case JSON serialization fails
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withHeaders(commonHeaders())
                    .withBody("{\"message\":\"serialization error\"}")
                    .build();
        }
    }

}

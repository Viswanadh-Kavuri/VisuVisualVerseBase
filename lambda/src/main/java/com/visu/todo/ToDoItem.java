package com.visu.todo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@DynamoDbBean
public class ToDoItem {
    private String id;
    private String title;
    private String createdAt;
    private boolean completed;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    @DynamoDbAttribute("title")
    public String getTitle() {
        return title;
    }

    @DynamoDbAttribute("createdAt")
    public String getCreatedAt() {
        return createdAt;
    }

    @DynamoDbAttribute("completed")
    public boolean isCompleted() {
        return completed;
    }
}

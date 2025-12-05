package com.visu.todo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

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
}

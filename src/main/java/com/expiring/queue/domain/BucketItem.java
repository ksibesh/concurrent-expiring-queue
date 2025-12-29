package com.expiring.queue.domain;

import lombok.Data;
import lombok.ToString;

import java.util.function.Consumer;

@Data
@ToString
public class BucketItem<T> {
    private String id;
    private T item;
    private long expiryTimestamp;
    private Consumer<T> consumer;
}

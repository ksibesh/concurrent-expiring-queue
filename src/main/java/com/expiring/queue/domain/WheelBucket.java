package com.expiring.queue.domain;

import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class WheelBucket<T> {
    private List<BucketItem<T>> bucketItems;
}

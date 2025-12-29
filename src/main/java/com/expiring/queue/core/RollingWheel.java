package com.expiring.queue.core;

import com.expiring.queue.domain.BucketItem;
import com.expiring.queue.domain.WheelBucket;
import com.expiring.queue.util.SystemUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.expiring.queue.util.SystemUtil.*;

public class RollingWheel<T> {

    private final WheelBucket<T>[] secondWheel;
    private final WheelBucket<T>[] minuteWheel;
    private final WheelBucket<T>[] hourWheel;
    private final WheelBucket<T>[] dayWheel;

    private final Map<String, BucketItem<T>> allItems;
    private final ExecutorService expiredTaskExecutor;
    private final ScheduledExecutorService scheduler;

    private volatile int secondCounter;
    private volatile int minuteCounter;
    private volatile int hourCounter;
    private volatile int dayCounter;

    public RollingWheel(int threadPool) {
        this.secondWheel = new WheelBucket[SECOND_WHEEL_SIZE];
        this.minuteWheel = new WheelBucket[MINUTE_WHEEL_SIZE];
        this.hourWheel = new WheelBucket[HOUR_WHEEL_SIZE];
        this.dayWheel = new WheelBucket[DAY_WHEEL_SIZE];

        this.allItems = new ConcurrentHashMap<>();
        this.expiredTaskExecutor = Executors.newFixedThreadPool(threadPool);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        long secPassedSinceInception = (System.currentTimeMillis() - SystemUtil.INCEPTION_TIMESTAMP) / 1000;
        this.dayCounter = (int) (secPassedSinceInception / SEC_IN_DAY) % DAY_WHEEL_SIZE;
        this.hourCounter = (int) (secPassedSinceInception / SEC_IN_HOUR) % HOUR_WHEEL_SIZE;
        this.minuteCounter = (int) (secPassedSinceInception / SEC_IN_MINUTE) % MINUTE_WHEEL_SIZE;
        this.secondCounter = (int) secPassedSinceInception % SECOND_WHEEL_SIZE;

        postConstruct();
    }

    private void postConstruct() {
        initializeWheelBucket(secondWheel, SECOND_WHEEL_SIZE);
        initializeWheelBucket(minuteWheel, MINUTE_WHEEL_SIZE);
        initializeWheelBucket(hourWheel, HOUR_WHEEL_SIZE);
        initializeWheelBucket(dayWheel, DAY_WHEEL_SIZE);

        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    private void initializeWheelBucket(WheelBucket<T>[] wheel, int size) {
        for (int i = 0; i < size; i++) {
            wheel[i] = new WheelBucket<>();
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        expiredTaskExecutor.shutdown();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow();
            if (!expiredTaskExecutor.awaitTermination(5, TimeUnit.SECONDS)) expiredTaskExecutor.shutdownNow();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            expiredTaskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public T delete(String id) {
        BucketItem<T> bucketItem = allItems.get(id);
        allItems.remove(id);
        return bucketItem != null ? bucketItem.getItem() : null;
    }

    public String insert(T item, long ttlInSec, Consumer<T> consumer) {
        String itemId = UUID.randomUUID().toString();
        long currentTime = System.currentTimeMillis();
        long expiryTimestamp = currentTime + (ttlInSec * 1000);

        BucketItem<T> bucketItem = new BucketItem<>();
        bucketItem.setId(itemId);
        bucketItem.setItem(item);
        bucketItem.setExpiryTimestamp(expiryTimestamp);
        bucketItem.setConsumer(consumer);

        insert(bucketItem, ttlInSec, expiryTimestamp);

        allItems.put(itemId, bucketItem);

        return itemId;
    }

    private void insert(BucketItem<T> bucketItem, long ttlInSec, long expiryTimestamp) {

        long expiryFromInceptionInSec = (expiryTimestamp - INCEPTION_TIMESTAMP) / 1000;

        if (ttlInSec > SEC_IN_DAY) {
            int dayIndex = (int) (expiryFromInceptionInSec / SEC_IN_DAY) % DAY_WHEEL_SIZE;
            threadSafeInsert(dayWheel[dayIndex], bucketItem);
        } else if (ttlInSec > SEC_IN_HOUR) {
            int hourIndex = (int) (expiryFromInceptionInSec / SEC_IN_HOUR) % HOUR_WHEEL_SIZE;
            threadSafeInsert(hourWheel[hourIndex], bucketItem);
        } else if (ttlInSec > SEC_IN_MINUTE) {
            int minIndex = (int) (expiryFromInceptionInSec / SEC_IN_MINUTE) % MINUTE_WHEEL_SIZE;
            threadSafeInsert(minuteWheel[minIndex], bucketItem);
        } else {
            int secIndex = (int) expiryFromInceptionInSec % SECOND_WHEEL_SIZE;
            threadSafeInsert(secondWheel[secIndex], bucketItem);
        }
    }

    private void threadSafeInsert(WheelBucket<T> bucket, BucketItem<T> bucketItem) {
        synchronized (bucket) {
            bucket.getBucketItems().add(bucketItem);
        }
    }

    private void executeExpiredItem(BucketItem<T> item) {
        if (allItems.get(item.getId()) == null) return;
        expiredTaskExecutor.submit(() -> item.getConsumer().accept(item.getItem()));
        allItems.remove(item.getId());
    }

    private void tick() {
        secondCounter = (secondCounter + 1) % SECOND_WHEEL_SIZE;
        // perform expiry
        WheelBucket<T> bucket = secondWheel[secondCounter];
        synchronized (bucket) {
            if (!bucket.getBucketItems().isEmpty()) {
                bucket.getBucketItems().forEach(this::executeExpiredItem);
                bucket.getBucketItems().clear();
            }
        }

        if (secondCounter == 0) {
            minuteCounter = (minuteCounter + 1) % MINUTE_WHEEL_SIZE;
            advanceLevel(minuteWheel, minuteCounter);

            if (minuteCounter == 0) {
                hourCounter = (hourCounter + 1) % HOUR_WHEEL_SIZE;
                advanceLevel(hourWheel, hourCounter);

                if (hourCounter == 0) {
                    dayCounter = (dayCounter + 1) % DAY_WHEEL_SIZE;
                    advanceLevel(dayWheel, dayCounter);
                }
            }
        }
    }

    private void advanceLevel(WheelBucket<T>[] wheel, int counter) {
        WheelBucket<T> bucket = wheel[counter];
        synchronized (bucket) {
            if (!bucket.getBucketItems().isEmpty()) {
                long currentTimestamp = System.currentTimeMillis();
                for (BucketItem<T> item : bucket.getBucketItems()) {
                    if (allItems.get(item.getId()) == null) continue;
                    long remainingTtl = (item.getExpiryTimestamp() - currentTimestamp) / 1000;
                    if (remainingTtl <= 0) executeExpiredItem(item);
                    else insert(item, remainingTtl, item.getExpiryTimestamp());
                }
                bucket.getBucketItems().clear();
            }
        }
    }

}

package com.winter.orders.api;

public class InvalidSortParameterException extends RuntimeException {

    public InvalidSortParameterException() {
        super("Sort must use the form createdAt,asc or createdAt,desc.");
    }
}

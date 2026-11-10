package com.webapps.flashsale.contract;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MessageType {
    ORDER_REQUESTED,
    ORDER_CREATED;

    @JsonCreator
    public static MessageType fromJson(String value) {
        return valueOf(value);
    }
}

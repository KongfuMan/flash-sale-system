package com.webapps.flashsale.contract;

public sealed interface MessagePayload permits OrderRequested, OrderCreated {}

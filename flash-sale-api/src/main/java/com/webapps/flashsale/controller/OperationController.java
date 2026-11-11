package com.webapps.flashsale.controller;

import com.webapps.flashsale.inventory.InventoryPrimitives;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/operation/")
public class OperationController {
    private final InventoryPrimitives inventory;

    public OperationController(InventoryPrimitives inventory) {
        this.inventory = inventory;
    }

    @PostMapping("flash_sale_event/{eventId}")
    public ResponseEntity<Void> prefill(@PathVariable long eventId) {
        try {
            if (!inventory.prefill(eventId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory is already initialized");
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

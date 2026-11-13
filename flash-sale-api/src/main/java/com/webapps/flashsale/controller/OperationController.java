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

    /**
     * Loads an event's current database inventory snapshot into its Redis Cluster hash.
     *
     * <p>Call this only while all inventory writers for the event are quiescent. The operation
     * refuses to overwrite an existing Redis inventory hash.</p>
     *
     * @param eventId event identifier to preload
     * @return 201 when the hash is created, 409 when it already exists, or 400 for invalid event data
     */
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

package com.fluveny.fluveny_backend.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Generic class representing the standard format for API responses.
 * <p>
 * Contains a message describing the response status or result,
 * and a data field holding the response payload of type T.
 *
 * @param <t> the type of the response data
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ApiResponseFormat<t> {
    private String message;
    private t data;
}

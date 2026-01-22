package io.numaproj.pulsar.producer;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataRecord {
    @JsonProperty("value")
    private long value;

    @JsonProperty("padding")
    private String padding;
}
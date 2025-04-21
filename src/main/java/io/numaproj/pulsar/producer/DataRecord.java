package io.numaproj.pulsar.producer;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataRecord {
    @JsonProperty("padding")
    @JsonAlias("Padding")
    private String padding;

    @JsonProperty("value")
    @JsonAlias("Value")
    private Long value;
}
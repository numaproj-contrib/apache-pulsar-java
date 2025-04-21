package io.numaproj.pulsar.producer;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NumagenMessage {
    @JsonProperty("createdts")
    @JsonAlias("Createdts")
    private Long createdts;

    @JsonProperty("data")
    @JsonAlias("Data")
    private DataRecord data;
}
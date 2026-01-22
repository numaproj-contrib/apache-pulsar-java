package io.numaproj.pulsar.producer;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class numagen {
    @JsonProperty("Data")
    private DataRecord Data;

    @JsonProperty("Createdts")
    private long Createdts;
}
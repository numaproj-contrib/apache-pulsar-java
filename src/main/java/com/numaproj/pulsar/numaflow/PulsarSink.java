package com.numaproj.pulsar.numaflow;

import io.numaproj.numaflow.sinker.Datum;
import io.numaproj.numaflow.sinker.DatumIterator;
import io.numaproj.numaflow.sinker.Response;
import io.numaproj.numaflow.sinker.ResponseList;
import io.numaproj.numaflow.sinker.Server;
import io.numaproj.numaflow.sinker.Sinker;
import lombok.extern.slf4j.Slf4j;

import org.apache.pulsar.client.api.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class PulsarSink extends Sinker {


    @Autowired
    private Producer<String> producer;

    private Server server;

    @PostConstruct // starts server automatically when the spring context initializes
    public void startServer() throws Exception {
        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    @Override
    public ResponseList processMessages(DatumIterator datumIterator) {
        ResponseList.ResponseListBuilder responseListBuilder = ResponseList.newBuilder();
        while (true) {
            Datum datum = null;
            try {
                datum = datumIterator.next();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                continue;
            }
            // null means the iterator is closed, so we break the loop
            if (datum == null) {
                break;
            }
            try {
                String msg = new String(datum.getValue());
                producer.send(msg);
                log.info("Processed message ID: {}", datum.getId());
                responseListBuilder.addResponse(Response.responseOK(datum.getId()));
            } catch (Exception e) {
                log.error("Error processing message with ID {}: {}", datum.getId(), e.getMessage(), e);
                responseListBuilder.addResponse(
                        Response.responseFailure(datum.getId(), e.getMessage())
                );
            }
        }
        return responseListBuilder.build();
    }
}

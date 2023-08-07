import lombok.experimental.FieldDefaults;
import org.apache.http.client.fluent.Content;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {

    private static final String URL = "https://ismp.crpt.ru/api/v3";
    private static final String CREATE = "/lk/documents/create";
    private final Limit limit;

    public Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit must be positive");
        }
        this.limit = new Limit(timeUnit, requestLimit);
    }

    public String createDocumentRF(Document doc, String signature) {

        Converter converterJson = new ConverterJson();
        String docJson = encodeBase64(convert(doc, converterJson));
        Body body = new Body(Document_Format.MANUAL, docJson, Type.LP_INTRODUCE_GOODS, signature);
        String bodyJson = convert(body, converterJson);

        return requestPost(URL.concat(CREATE), bodyJson);
    }

    private String encodeBase64(String data) {
        return new String(Base64.getEncoder().encode(data.getBytes()));
    }

    private String convert(Object body, Converter converter) {
        return converter.convert(body);
    }

    private String requestPost(String url, String bodyString) {

        Content postResult = null;
        try {
            if (limit.getLimit() >= 0) {
                postResult = Request.Post(url)
                        .bodyString(bodyString, ContentType.APPLICATION_JSON)
                        .execute().returnContent();
            } else {
                log.info("the request limit has ended in this time interval");
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return postResult != null ? postResult.asString() : "";
    }

    public Document createDocument(String documentId, String documentStatus,
                                   String documentType, boolean importRequest, String participantInn,
                                   String producerInn, String productionDate, String productionType,
                                   List<Product> products, String regDate, String regNumber) {

        Description description = this.new Description(participantInn);

        return this.new Document(description, documentId, documentStatus, documentType, importRequest,
                participantInn, producerInn, productionDate, productionType, products, regDate, regNumber);
    }

    private class Limit {
        private final TimeUnit timeUnit;
        private final int requestLimit;

        private AtomicInteger limit;
        private final AtomicBoolean checkTime;

        public Limit(TimeUnit timeUnit, int requestLimit) {
            this.timeUnit = timeUnit;
            this.requestLimit = requestLimit;
            this.limit = new AtomicInteger(requestLimit);
            this.checkTime = new AtomicBoolean(true);
        }

        int getLimit() {
            if (checkTime.compareAndSet(true, false)) {
                limit = new AtomicInteger(requestLimit);
                new Thread(() -> {
                    try {
                        Thread.sleep(timeUnit.toMillis(1));
                        checkTime.set(true);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage());
                    }
                }).start();
            }
            return limit.decrementAndGet();
        }
    }

    class ConverterJson implements Converter {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public String convert(Object body) {
            try (StringWriter writer = new StringWriter()) {
                mapper.writeValue(writer, body);
                return writer.toString();
            } catch (IOException e) {
                log.error(e.getMessage());
            }
            return "";
        }
    }

    @Data
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public class Document {

        Description description;
        String documentId;
        String documentStatus;
        String documentType;
        boolean importRequest;
        String participantInn;
        String producerInn;
        String productionDate;
        String productionType;
        List<Product> products;
        String regDate;
        String regNumber;
    }

    @Data
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public class Product {
        String certificateDocument;
        String certificateDocumentDate;
        String certificateDocumentNumber;
        String ownerInn;
        String producerInn;
        String productionDate;
        String tnvedCode;
        String uitCode;
        String uituCode;
    }

    @Data
    @AllArgsConstructor
    public class Description {
        private String participantInn;
    }

    @Data
    @AllArgsConstructor
    class Body {

        private Document_Format documentFormat;
        private String productDocument;
        private Type type;
        private String signature;
    }

    interface Converter {
        String convert(Object body);
    }

    enum Document_Format {
        MANUAL,
        CSV,
        XML
    }

    public enum Type {
        LP_INTRODUCE_GOODS
    }
}

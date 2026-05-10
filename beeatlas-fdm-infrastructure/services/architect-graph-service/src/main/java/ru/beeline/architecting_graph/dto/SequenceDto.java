package ru.beeline.architecting_graph.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import javax.validation.constraints.*;
import javax.validation.Valid;
import java.util.List;

/**
 * DTO, представляющий диаграмму и её последовательность шагов.
 */
@Data
@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public final class SequenceDto {

    @NotBlank(message = "diagramKey is required")
    private final String diagramKey;

    @NotEmpty(message = "sequence must not be empty")
    @Valid
    private final List<@NotNull @Valid SequenceItemDto> sequence;

    @JsonCreator
    public SequenceDto(
            @JsonProperty(value = "diagramKey", required = true) String diagramKey,
            @JsonProperty(value = "sequence", required = true) List<SequenceItemDto> sequence
    ) {
        this.diagramKey = diagramKey;
        this.sequence = sequence;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static final class SequenceItemDto {

        @NotBlank(message = "method is required")
        private final String method;

        @NotBlank(message = "tcCode is required")
        private final String tcCode;

        @NotNull(message = "out is required")
        @Valid
        private final ComponentDto out;

        @NotNull(message = "in is required")
        @Valid
        private final ComponentDto in;

        @NotNull(message = "order is required")
        @Min(value = 1, message = "order must be >= 1")
        private final Integer order;

        @JsonCreator
        public SequenceItemDto(
                @JsonProperty(value = "method", required = true) String method,
                @JsonProperty(value = "tcCode", required = true) String tcCode,
                @JsonProperty(value = "out", required = true) ComponentDto out,
                @JsonProperty(value = "in", required = true) ComponentDto in,
                @JsonProperty(value = "order", required = true) Integer order
        ) {
            this.method = method;
            this.tcCode = tcCode;
            this.out = out;
            this.in = in;
            this.order = order;
        }

        public String getMethod() {
            return method;
        }

        public String getTcCode() {
            return tcCode;
        }

        public ComponentDto getOut() {
            return out;
        }

        public ComponentDto getIn() {
            return in;
        }

        public Integer getOrder() {
            return order;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = false)
    public static final class ComponentDto {

        @NotBlank(message = "softwaresystem is required")
        private final String softwaresystem;

        @NotBlank(message = "container is required")
        private final String container;

        @NotBlank(message = "component is required")
        private final String component;

        @JsonCreator
        public ComponentDto(
                @JsonProperty(value = "softwaresystem", required = true) String softwaresystem,
                @JsonProperty(value = "container", required = true) String container,
                @JsonProperty(value = "component", required = true) String component
        ) {
            this.softwaresystem = softwaresystem;
            this.container = container;
            this.component = component;
        }

        public String getSoftwaresystem() {
            return softwaresystem;
        }

        public String getContainer() {
            return container;
        }

        public String getComponent() {
            return component;
        }
    }
}
package org.egov.pt.web.contracts;

import org.egov.common.contract.response.ResponseInfo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AssessmentDeleteResponse   {
	
        @JsonProperty("ResponseInfo")
        private ResponseInfo responseInfo ;

        @JsonProperty("tenantId")
        private String tenantId ;

        @JsonProperty("assessmentNumber")
        private String assessmentNumber ;
}


/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.canary;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.trace.TraceRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Greg Turnquist
 */
@RestController
public class AnalysisController {

	private final AnalysisService analysisService;
	private final TraceRepository traceRepository;
	private final ObjectMapper objectMapper;

	@Autowired
	public AnalysisController(AnalysisService analysisService, TraceRepository traceRepository, ObjectMapper objectMapper) {

		this.analysisService = analysisService;
		this.traceRepository = traceRepository;
		this.objectMapper = objectMapper;
	}

	@RequestMapping(method = RequestMethod.GET, value = "/analyze")
	public Analysis clusterAnalyzer(@RequestParam("cluster") URI cluster,
									@RequestParam(name = "statusOnly", required = false, defaultValue = "false") String statusOnly) throws IOException {

		final Analysis analysis = this.analysisService.analyze(cluster, Boolean.valueOf(statusOnly));

		traceAnalysis(cluster, statusOnly, analysis);

		return analysis;
	}

	/**
	 * Record all the details of the request along with the results in Spring Boot's {@link TraceRepository}.
	 *
	 * @param cluster
	 * @param statusOnly
	 * @param analysis
	 * @throws IOException
	 */
	private void traceAnalysis(URI cluster, String statusOnly, Analysis analysis) throws IOException {

		final String analysisJson = objectMapper.writeValueAsString(analysis);
		final Map analysisMap = objectMapper.readValue(analysisJson, Map.class);

		Map aggregateMap = new HashMap<>();
		aggregateMap.put("request", cluster);
		aggregateMap.put("statusOnly", statusOnly);
		aggregateMap.put("results", analysisMap);

		traceRepository.add(aggregateMap);
	}
}

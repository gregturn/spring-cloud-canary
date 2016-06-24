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

import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.canary.clusterdetails.ClusterDetails;
import org.springframework.cloud.canary.clusterdetails.ServerGroup;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @author Greg Turnquist
 */
@Service
public class AnalysisService {

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

	private final RestTemplate restTemplate;
	private final CanaryCriteria canaryCriteria;

	@Autowired
	public AnalysisService(RestTemplate restTemplate, CanaryCriteria canaryCriteria) {

		this.restTemplate = restTemplate;
		this.canaryCriteria = canaryCriteria;
	}

	public Analysis analyze(URI cluster, boolean statusOnly) {

		ClusterDetails clusterDetails = restTemplate.getForObject(cluster, ClusterDetails.class);

		if (clusterDetails.getServerGroups().size() != 2 ) {
			return SimpleAnalysis.builder()
				.status(Status.INVALID)
				.message("This canary analysis is for two server groups. Instead found " + clusterDetails.getServerGroups().size())
				.build();
		}

		Map<String, List<String>> rawData = clusterDetails.getServerGroups().stream()
				.sorted(AnalysisService::createdTimeComparator)
				.map(AnalysisService::findVersionSpecificUri)
				.map(AnalysisService::transformIntoMetricsUri)
				.filter(metricUri -> !metricUri.isEmpty())
				.map(this::fetchMetricData)
				.map(this::collectSubsetOfMetricData)
				.reduce(new HashMap<>(), (results, thisServerGroupMetrics) -> {
					thisServerGroupMetrics.entrySet().stream()
						.forEach(thisTimeEntry -> {
							results.computeIfAbsent(thisTimeEntry.getKey(), s -> new ArrayList<>())
									.add(thisTimeEntry.getValue().get(0));
						});
					return results;
				});

		Map<String, String> analyzedStuff = rawData.entrySet().stream()
				.map(AnalysisService::calculatePercentIncrease)
				.collect(Collectors.toMap(
						collatedMetric -> collatedMetric.getKey(),
						collatedMetric -> collatedMetric.getValue()));

		List<SimpleAnalysis> results = canaryCriteria.getMetrics().stream()
				.map(metric -> {
					if (canaryCriteria.withinLimit(analyzedStuff.getOrDefault(metric + ".percentIncrease", "0.0%"))) {
						return SimpleAnalysis.builder()
								.status(Status.UP)
								.message("New server group's " + metric + " is within " + canaryCriteria.getLimit() + " of old server group")
								.rawData(rawData)
								.analyzedData(analyzedStuff)
								.build();
					} else {
						return SimpleAnalysis.builder()
								.status(Status.DEGRADED)
								.message("New server group's " + metric + " is outside " + canaryCriteria.getLimit() + " of old server group")
								.rawData(rawData)
								.analyzedData(analyzedStuff)
								.build();
					}
				})
				.collect(Collectors.toList());

		final CompositeAnalysis compositeAnalysis = CompositeAnalysis.builder()
				.analysisList(results)
				.build();

		if (statusOnly) {
			return () -> compositeAnalysis.getStatus();
		} else {
			return compositeAnalysis;
		}

	}

	private static int createdTimeComparator(ServerGroup o1, ServerGroup o2) {
		return o2.getCreatedTime().compareTo(o1.getCreatedTime());
	}

	private static Optional<String> findVersionSpecificUri(ServerGroup serverGroup) {

		return serverGroup.getNativeApplication().getUris().stream()
				.filter(s -> s.contains(serverGroup.getName()))
				.findFirst();
	}

	private static String transformIntoMetricsUri(Optional<String> optionalServerGroupUri) {
		return optionalServerGroupUri.map(s -> "http://" + s + "/metrics").orElse("");
	}

	private Map<String, String> fetchMetricData(String metricUri) {
		return restTemplate.exchange(
				metricUri,
				HttpMethod.GET,
				RequestEntity.EMPTY,
				new ParameterizedTypeReference<Map<String, String>>() {})
				.getBody();
	}

	private Map<String, List<String>> collectSubsetOfMetricData(Map<String, String> metricData) {

		final Map<String, List<String>> collect = canaryCriteria.getMetrics().stream()
				.collect(Collectors.toMap(
						metricName -> metricName,
						metricName -> Collections.singletonList(metricData.getOrDefault(metricName, ""))));
		return collect;
	}

	private static AbstractMap.SimpleEntry<String, String> calculatePercentIncrease(Map.Entry<String, List<String>> entry) {

		float oldValue = new Long(entry.getValue().get(0)).floatValue();
		float newValue = new Long(entry.getValue().get(1)).floatValue();
		return new AbstractMap.SimpleEntry<>(
				entry.getKey() + ".percentIncrease",
				100.0f * (newValue - oldValue) / oldValue + "%");
	}

}

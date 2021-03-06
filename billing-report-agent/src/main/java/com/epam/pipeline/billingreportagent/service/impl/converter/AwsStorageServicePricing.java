/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.pricing.AWSPricing;
import com.amazonaws.services.pricing.AWSPricingClientBuilder;
import com.amazonaws.services.pricing.model.Filter;
import com.amazonaws.services.pricing.model.GetProductsRequest;
import com.amazonaws.services.pricing.model.GetProductsResult;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class AwsStorageServicePricing {

    private static final int CENTS_IN_DOLLAR = 100;

    private final Map<Regions, StoragePricing> storagePriceListGb = new HashMap<>();

    /**
     * Storage service name (either "AmazonS3" or "AmazonEFS")
     */
    private final String awsStorageServiceName;

    /**
     * The highest price all over available regions
     */
    private BigDecimal defaultPriceGb;

    public AwsStorageServicePricing(final String awsStorageServiceName) {
        this.awsStorageServiceName = awsStorageServiceName;
        updatePrices();
    }

    public AwsStorageServicePricing(final String awsStorageServiceName,
                                    final Map<Regions, StoragePricing> initialPriceList) {
        this.awsStorageServiceName = awsStorageServiceName;
        this.storagePriceListGb.putAll(initialPriceList);
        this.defaultPriceGb = calculateDefaultPriceGb();
    }

    public void updatePrices() {
        loadFullPriceList(awsStorageServiceName).forEach(price -> {
            try {
                final JsonNode regionInfo = new ObjectMapper().readTree(price);
                final String regionName = regionInfo.path("product").path("attributes").path("location").asText();
                getRegionFromFullLocation(regionName).ifPresent(region -> fillPricingInfoForRegion(region, regionInfo));
            } catch (IOException e) {
                log.error("Can't instantiate AWS storage price list!");
            }
        });
        defaultPriceGb = calculateDefaultPriceGb();
    }

    public BigDecimal getDefaultPriceGb() {
        return defaultPriceGb;
    }

    public StoragePricing getRegionPricing(final Regions region) {
        return storagePriceListGb.get(region);
    }

    private void fillPricingInfoForRegion(final Regions region, final JsonNode regionInfo) {
        final StoragePricing pricing = new StoragePricing();
        regionInfo.findParents("pricePerUnit").stream()
            .map(this::extractPricingFromJson)
            .forEach(pricing::addPrice);
        storagePriceListGb.put(region, pricing);
    }

    private BigDecimal calculateDefaultPriceGb() {
        return storagePriceListGb.values()
            .stream()
            .flatMap(pricing -> pricing.getPrices().stream())
            .map(StoragePricing.StoragePricingEntity::getPriceCentsPerGb)
            .filter(price -> !BigDecimal.ZERO.equals(price))
            .max(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalStateException("No AWS storage prices loaded!"));
    }

    private List<String> loadFullPriceList(final String awsStorageServiceName) {
        final List<String> allPrices = new ArrayList<>();
        final Filter filter = new Filter();
        filter.setType("TERM_MATCH");
        filter.setField("productFamily");
        filter.setValue("Storage");
        filter.setField("storageClass");
        filter.setValue("General Purpose");

        String nextToken = StringUtils.EMPTY;
        do {
            final GetProductsRequest request = new GetProductsRequest()
                .withServiceCode(awsStorageServiceName)
                .withFilters(filter)
                .withNextToken(nextToken)
                .withFormatVersion("aws_v1");

            final AWSPricing awsPricingService = AWSPricingClientBuilder
                .standard()
                .withRegion(Regions.US_EAST_1)
                .build();

            final GetProductsResult result = awsPricingService.getProducts(request);
            allPrices.addAll(result.getPriceList());
            nextToken = result.getNextToken();
        } while (nextToken != null);
        return allPrices;
    }

    private Optional<Regions> getRegionFromFullLocation(final String location) {
        for (Regions region : Regions.values()) {
            if (region.getDescription().equals(location)) {
                return Optional.of(region);
            }
        }
        log.warn("Can't parse location: " + location);
        return Optional.empty();
    }

    private StoragePricing.StoragePricingEntity extractPricingFromJson(final JsonNode priceDimension) {
        final BigDecimal priceGb =
            new BigDecimal(priceDimension.path("pricePerUnit").path("USD").asDouble(), new MathContext(
                AwsStorageToBillingRequestConverter.PRECISION))
                .multiply(BigDecimal.valueOf(CENTS_IN_DOLLAR));
        final long beginRange =
            priceDimension.path("beginRange").asLong() * AwsStorageToBillingRequestConverter.BYTES_TO_GB;
        final long endRange = priceDimension.path("endRange").asLong();
        return new StoragePricing.StoragePricingEntity(beginRange,
                                                       endRange == 0
                                                       ? Long.MAX_VALUE
                                                       : endRange * AwsStorageToBillingRequestConverter.BYTES_TO_GB,
                                                       priceGb);
    }
}

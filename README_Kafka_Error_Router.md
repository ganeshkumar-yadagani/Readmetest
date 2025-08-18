package com.tmobile.deep.service.v4.impl;

import com.tmobile.deep.service.DeepCustomConfigService;
import com.tmobile.deep.service.DeepDefaultConfigService;
import com.tmobile.deep.service.dto.DeepDefaultConfigDTO;
import com.tmobile.deep.service.dto.PublisherConfigDTO;
import com.tmobile.deep.service.entity.PublisherConfig;
import com.tmobile.deep.service.enums.EntityType;
import com.tmobile.deep.service.v4.LargeFileConfigServiceV4;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LargeFileConfigServiceV4Impl implements LargeFileConfigServiceV4 {

    @Autowired
    private DeepDefaultConfigService defaultConfigService;

    @Autowired
    private DeepCustomConfigService customConfigService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String MAX_EVENT_SIZE_IN_KB_PROP = "deep.event.largefile.maxEventSizeInKb";

    @Override
    public LargeFilePublisherConfigDTO buildLargeFilePublisherConfigDTO(String env, String producer) {
        // 1) Load all defaults
        List<EntityType> entityTypes = Arrays.asList(EntityType.APPLICATION, EntityType.EVENT, EntityType.PUBLISHER);
        Map<EntityType, List<DeepDefaultConfigDTO>> defaultConfigAll =
                defaultConfigService.findByEntityTypesAndEnv(env, entityTypes, null);

        List<DeepDefaultConfigDTO> defaultApplicationProps = defaultConfigAll.get(EntityType.APPLICATION);
        List<DeepDefaultConfigDTO> defaultEventProps = defaultConfigAll.get(EntityType.EVENT);
        List<DeepDefaultConfigDTO> defaultPublisherProps = defaultConfigAll.get(EntityType.PUBLISHER);

        // 2) Get publisher-specific config (entity list)
        List<PublisherConfig> publisherConfig = customConfigService.findPublisherConfig(producer, env);

        // 3) Build finalPublisherConfig (all defaults with PUBLISHER merged with publisherConfig)
        List<PublisherConfigDTO> finalPublisherConfig = buildFinalPublisherConfigDTOs(defaultConfigAll, publisherConfig, producer);

        // 4) Your existing event size logic (unchanged)
        String maxEventSize = getPropertyValue(defaultEventProps, MAX_EVENT_SIZE_IN_KB_PROP);
        Integer maxEventSizeKb = (maxEventSize == null || maxEventSize.trim().isEmpty())
                ? null : Integer.parseInt(maxEventSize);

        List<EventFileTypeConfig> eventFileTypeConfigs =
                getEventFileTypeConfigDTO(defaultEventProps, getEventFileTypeConfigByPublisherAndEnv(producer, env), maxEventSizeKb);

        List<EventFileTypeConfig> eventFileTypeAndCustomSize =
                getEventFileTypeConfigDTO(defaultEventProps, eventFileTypeConfigs, maxEventSizeKb);

        // 5) Build final DTO (unchanged fields)
        return LargeFilePublisherConfigDTO.builder()
                .env(env)
                .publisher(producer)
                .applicationDefaults(defaultApplicationProps)
                .eventDefaults(defaultEventProps)
                .publisherDefaults(defaultPublisherProps)
                .publisherConfig(finalPublisherConfig)
                .eventFileTypes(eventFileTypeAndCustomSize)
                .build();
    }

    // =========================================================================
    // Option 2: Single-loop merge (keep EntityType; overrides only for PUBLISHER)
    // =========================================================================
    private List<PublisherConfigDTO> buildFinalPublisherConfigDTOs(
            Map<EntityType, List<DeepDefaultConfigDTO>> defaultConfigAll,
            List<PublisherConfig> publisherConfig,
            String producer
    ) {
        // Resolve publisher name (prefer entity list if present)
        String publisherName = (publisherConfig != null && !publisherConfig.isEmpty())
                ? publisherConfig.get(0).getPublisherName()
                : producer;

        // Merge PUBLISHER defaults with publisherConfig overrides (by propertyName)
        LinkedHashMap<String, String> publisherMerged = new LinkedHashMap<>();
        List<DeepDefaultConfigDTO> defaultPublisherProps =
                defaultConfigAll.getOrDefault(EntityType.PUBLISHER, Collections.emptyList());

        // baseline: PUBLISHER defaults
        for (DeepDefaultConfigDTO d : defaultPublisherProps) {
            publisherMerged.put(d.getPropertyName(), d.getPropertyValue());
        }
        // overrides: publisherConfig (non-blank only)
        if (publisherConfig != null) {
            for (PublisherConfig p : publisherConfig) {
                String v = p.getPropertyValue();
                if (v != null && !v.trim().isEmpty()) {
                    publisherMerged.put(p.getPropertyName(), v);
                }
            }
        }

        // Build final list in a single loop over defaultConfigAll
        List<PublisherConfigDTO> out = new ArrayList<>();
        for (Map.Entry<EntityType, List<DeepDefaultConfigDTO>> entry : defaultConfigAll.entrySet()) {
            EntityType et = entry.getKey();
            List<DeepDefaultConfigDTO> defaults = entry.getValue();

            if (et == EntityType.PUBLISHER) {
                // use merged publisher map
                for (Map.Entry<String, String> e : publisherMerged.entrySet()) {
                    out.add(new PublisherConfigDTO(publisherName, e.getKey(), e.getValue(), EntityType.PUBLISHER));
                }
            } else {
                // APPLICATION / EVENT pass-through (preserve their entity type)
                if (defaults != null) {
                    for (DeepDefaultConfigDTO d : defaults) {
                        out.add(new PublisherConfigDTO(publisherName, d.getPropertyName(), d.getPropertyValue(), et));
                    }
                }
            }
        }

        return out;
    }

    // =========================================================================
    // Existing helpers (left as-is / minimal changes)
    // =========================================================================
    private List<EventFileTypeConfig> getEventFileTypeConfigByPublisherAndEnv(String publisher, String env) {
        return entityManager
                .createNamedQuery("EventFileTypeConfig.findByPublisherAndEnv", EventFileTypeConfig.class)
                .setParameter(1, publisher)
                .setParameter(2, env)
                .getResultList();
    }

    private List<EventFileTypeConfig> getEventCustomConfigByPublisherAndEnv(String publisher, String env) {
        return entityManager
                .createNamedQuery("EventFileTypeConfig.findCustomByPublisherAndEnv", EventFileTypeConfig.class)
                .setParameter(1, publisher)
                .setParameter(2, env)
                .getResultList();
    }

    private List<EventFileTypeConfig> getEventFileTypeConfigDTO(List<DeepDefaultConfigDTO> defaultEventProps,
                                                                List<EventFileTypeConfig> eventFileTypeConfigs,
                                                                Integer maxEventSizeKb) {
        // keep your existing mapping logic
        return eventFileTypeConfigs;
    }

    private String getPropertyValue(List<DeepDefaultConfigDTO> defaultProps, String key) {
        DeepDefaultConfigDTO config = defaultProps.stream()
                .filter(c -> key.equals(c.getPropertyName()))
                .findFirst()
                .orElseThrow(() -> new MissingPublisherConfigException("Value missing in database config"));
        return config.getPropertyValue();
    }
}

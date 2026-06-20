package com.katixo.ai.config;

import com.katixo.ai.commons.gpu.GpuGuardConfig;
import com.katixo.ai.commons.gpu.GpuResourceGuard;
import com.katixo.ai.commons.gpu.InProcessGpuGuard;
import com.katixo.ai.commons.gpu.PostgresAdvisoryGpuGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Wires the shared {@link GpuResourceGuard}. In {@code postgres} mode it builds a dedicated
 * lock-authority DataSource (separate from the app's JPA datasource — the advisory lock needs its own
 * connection and no tables/DDL). In {@code in-process} mode (tests) it uses a single-JVM semaphore so
 * the offline/H2 suite never touches {@code pg_advisory_lock}.
 */
@Configuration
@EnableConfigurationProperties(GpuGuardProperties.class)
public class GpuGuardConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GpuGuardConfiguration.class);

    @Bean
    public GpuResourceGuard gpuResourceGuard(GpuGuardProperties props) {
        GpuGuardConfig.Mode mode = "in-process".equalsIgnoreCase(props.getLockMode())
                ? GpuGuardConfig.Mode.IN_PROCESS
                : GpuGuardConfig.Mode.POSTGRES;
        GpuGuardConfig cfg = new GpuGuardConfig(props.getLockKey(), props.getAcquireTimeout(),
                props.getPollInterval(), props.getMaxHold(), mode);

        if (mode == GpuGuardConfig.Mode.IN_PROCESS) {
            log.info("GPU guard: in-process mode (single-JVM only; not cross-process).");
            return new InProcessGpuGuard(cfg);
        }

        DataSource lockAuthority = DataSourceBuilder.create()
                .url(props.getLockDatasource().getUrl())
                .username(props.getLockDatasource().getUsername())
                .password(props.getLockDatasource().getPassword())
                .build();
        log.info("GPU guard: postgres advisory-lock mode, lock authority = {}",
                props.getLockDatasource().getUrl());
        return new PostgresAdvisoryGpuGuard(lockAuthority, cfg);
    }
}

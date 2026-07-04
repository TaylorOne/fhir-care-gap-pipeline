package io.github.taylorone.fhirpipeline.gapanalysis.bigquery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import io.github.taylorone.fhirpipeline.gapanalysis.config.GapAnalysisProperties;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.MeasureDefinition;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.PatientEvaluation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs measure SQL as parameterized BigQuery queries. {@code @run_date} is a
 * named parameter; the dataset prefix is the one thing that must be
 * interpolated (table names cannot be bound), which is why
 * {@link GapAnalysisProperties} constrains it to {@code [A-Za-z0-9_.-]}.
 */
@Component
@Profile("!local")
public class BigQueryMeasureRunner implements MeasureRunner {

    private static final Logger log = LoggerFactory.getLogger(BigQueryMeasureRunner.class);

    private final BigQuery bigQuery;
    private final GapAnalysisProperties properties;

    public BigQueryMeasureRunner(BigQuery bigQuery, GapAnalysisProperties properties) {
        this.bigQuery = bigQuery;
        this.properties = properties;
    }

    @Override
    public List<PatientEvaluation> evaluate(MeasureDefinition measure, LocalDate runDate) {
        QueryJobConfiguration query = QueryJobConfiguration
                .newBuilder(bindDataset(measure.sql(), properties.qualifiedDataset()))
                .addNamedParameter("run_date", QueryParameterValue.date(runDate.toString()))
                .build();

        List<PatientEvaluation> evaluations = new ArrayList<>();
        try {
            for (FieldValueList row : bigQuery.query(query).iterateAll()) {
                evaluations.add(new PatientEvaluation(
                        row.get("patient_id").getStringValue(),
                        row.get("in_numerator").getBooleanValue(),
                        row.get("last_evidence_date").isNull()
                                ? null
                                : LocalDate.parse(row.get("last_evidence_date").getStringValue())));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running measure " + measure.id(), e);
        }
        log.info("Measure {} evaluated {} denominator patients", measure.id(), evaluations.size());
        return evaluations;
    }

    static String bindDataset(String sql, String qualifiedDataset) {
        if (!qualifiedDataset.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Unsafe dataset reference: " + qualifiedDataset);
        }
        return sql.replace("${dataset}", qualifiedDataset);
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.pipeline.router;

import org.opensearch.dataprepper.parser.DataFlowComponent;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.pipeline.PipelineConnector;

import org.opensearch.dataprepper.model.trace.Span;
import org.opensearch.dataprepper.model.trace.JacksonSpan;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.JacksonEvent;
import org.opensearch.dataprepper.model.event.EventFactory;
import org.opensearch.dataprepper.model.acknowledgements.AcknowledgementSetManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class RouterCopyRecordStrategy implements RouterGetRecordStrategy {
    private Set<Record> routedRecords;
    private AcknowledgementSetManager acknowledgementSetManager;
    private EventFactory eventFactory;

    public <C> RouterCopyRecordStrategy(final EventFactory eventFactory, final AcknowledgementSetManager acknowledgementSetManager, final Collection<DataFlowComponent<C>> dataFlowComponents) {
        this.acknowledgementSetManager = acknowledgementSetManager;
        this.eventFactory = eventFactory;
        routedRecords = null;
        /*
         * If there are more than one sink and one of the sinks is
         * pipeline connector, then we should make a copy of every
         * record that is routed to more than one sink, so, to keep
         * track of already routed records, initialize the set.
         */
        if (dataFlowComponents.size() > 1) {
            for (DataFlowComponent<C> dataFlowComponent : dataFlowComponents) {
                if (dataFlowComponent.getComponent() instanceof PipelineConnector) {
                    routedRecords = new HashSet<Record>();
                    break;
                }
            }
        }
    }

    @Override
    public Record getRecord(Record record) {
        if (routedRecords == null) {
            return record;
        }
        if (!routedRecords.contains(record)) {
            routedRecords.add(record);
            return record;
        }
        if (record.getData() instanceof JacksonSpan) {
            try {
                final Span spanEvent = (Span) record.getData();
                Span newSpanEvent = JacksonSpan.fromSpan(spanEvent);
                return new Record<>(newSpanEvent);
            } catch (Exception ex) {
            }
        } else if (record.getData() instanceof Event) {
            try {
                final Event recordEvent = (Event) record.getData();
                Event newRecordEvent = JacksonEvent.fromEvent(recordEvent);
                return new Record<>(newRecordEvent);
            } catch (Exception ex) {
            }
        }
        return record;
    }

    @Override
    public Collection<Record> getAllRecords(final Collection<Record> allRecords) {
        if (routedRecords == null) {
            return allRecords;
        }
        if (routedRecords.isEmpty()) {
            routedRecords.addAll(allRecords);
            return allRecords;
        } 
        List<Record> newRecords = new ArrayList<Record>();
        for (Record record : allRecords) {
            newRecords.add(getRecord(record));
        }
        return newRecords;
    }
}

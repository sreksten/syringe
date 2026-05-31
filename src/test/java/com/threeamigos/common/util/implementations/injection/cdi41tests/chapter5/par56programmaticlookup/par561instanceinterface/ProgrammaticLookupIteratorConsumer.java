package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationLiteral;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProgrammaticLookupIteratorConsumer {

    @Inject
    @Any
    private Instance<PlainIteratorCandidate> plainCandidatesAny;

    public List<String> iterateAnyCandidateIds() {
        List<String> ids = new ArrayList<String>();
        for (PlainIteratorCandidate candidate : plainCandidatesAny) {
            ids.add(candidate.id());
        }
        return ids;
    }

    public int iterateUnsatisfiedCandidateCount() {
        int count = 0;
        for (PlainIteratorCandidate ignored : plainCandidatesAny.select(AnnotationLiteral.of(MissingQualifier.class))) {
            count++;
        }
        return count;
    }

    public List<String> streamAnyCandidateIds() {
        return plainCandidatesAny.stream()
                .map(PlainIteratorCandidate::id)
                .collect(Collectors.toList());
    }

    public long streamUnsatisfiedCandidateCount() {
        return plainCandidatesAny.select(AnnotationLiteral.of(MissingQualifier.class))
                .stream()
                .count();
    }

    public List<String> handleAnyCandidateIds() {
        List<String> ids = new ArrayList<String>();
        for (Instance.Handle<PlainIteratorCandidate> handle : plainCandidatesAny.handles()) {
            ids.add(handle.get().id());
        }
        return ids;
    }

    public boolean handlesIterableCreatesNewHandleSetPerIteratorCall() {
        Iterable<? extends Instance.Handle<PlainIteratorCandidate>> iterable = plainCandidatesAny.handles();

        Set<Integer> firstSet = new HashSet<Integer>();
        for (Instance.Handle<PlainIteratorCandidate> handle : iterable) {
            firstSet.add(System.identityHashCode(handle));
        }

        Set<Integer> secondSet = new HashSet<Integer>();
        for (Instance.Handle<PlainIteratorCandidate> handle : iterable) {
            secondSet.add(System.identityHashCode(handle));
        }

        if (firstSet.isEmpty() && secondSet.isEmpty()) {
            return true;
        }
        Set<Integer> intersection = new HashSet<Integer>(firstSet);
        intersection.retainAll(secondSet);
        return intersection.isEmpty();
    }

    public List<String> handleStreamAnyCandidateIds() {
        return plainCandidatesAny.handlesStream()
                .map(handle -> handle.get().id())
                .collect(Collectors.toList());
    }
}

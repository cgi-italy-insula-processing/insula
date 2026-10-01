package com.cgi.eoss.platform.core.processing.worker.kubernetes.utils;

import com.cgi.eoss.platform.rpc.JobParam;
import lombok.NoArgsConstructor;

import java.util.Comparator;

/**
 * Utility class to compare two JobParams, useful to sort a JobParam list
 */
@NoArgsConstructor
public class JobParamComparator implements Comparator<JobParam> {

    /**
     * Compare two job param following these criteria:
     * <ul>
     *     <li>Return result > 0 if o1 has HIGHER position than o2</li>
     *     <li>Return result < 0 if o1 has LOWER position than o2</>
     *     <li>If the position inside JobParam inputBinding is equal then compare jobParam name alphabetically</li>
     * </ul>
     * @param o1 the first object to be compared.
     * @param o2 the second object to be compared.
     * @return
     *      The compare result between o1 and o2,
     *      if result < 0 then o1 < o2;
     *      if result > 0 then o1 > o2;
     *      if result = 0 then o1 = o2
     */
    @Override
    public int compare(JobParam o1, JobParam o2) {
        int result = o1.getInputBinding().getPosition() - o2.getInputBinding().getPosition();
        if (result == 0) {
            return o1.getParamName().compareTo(o2.getParamName());
        }
        return result;
    }
}

package org.hisp.dhis.reservedvalue;

import org.hisp.dhis.scheduling.AbstractJob;
import org.hisp.dhis.scheduling.JobConfiguration;
import org.hisp.dhis.scheduling.JobType;

/**
 * @author Henning Håkonsen
 */
public class RemoveExpiredReservedValuesJob
    extends AbstractJob
{
    private ReservedValueStore reservedValueStore;

    public void setReservedValueStore( ReservedValueStore reservedValueStore )
    {
        this.reservedValueStore = reservedValueStore;
    }

    // Should be removed when merged with master. old scheduler in this branch
    @Override
    protected String getJobId()
    {
        return null;
    }

    @Override
    public JobType getJobType()
    {
        return JobType.REMOVE_EXPIRED_RESERVED_VALUES;
    }

    @Override
    public void execute( JobConfiguration jobConfiguration )
        throws Exception
    {
        reservedValueStore.removeExpiredReservations();
    }
}

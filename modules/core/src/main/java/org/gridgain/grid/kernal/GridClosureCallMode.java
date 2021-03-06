/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.compute.*;
import org.jetbrains.annotations.*;

/**
 * Distribution modes for one or more closures executed via {@code Grid.call(...)} methods.
 * In other words, given a set of jobs (closures) and set of grid nodes this enumeration provides
 * three simple modes of how these jobs will be mapped to the nodes.
 * <p>
 * <b>Note</b> that if you need to provide custom distribution logic you need to
 * implement {@link GridComputeTask} interface that allows you to customize every aspect of a
 * distributed Java code execution such as  mapping, load balancing, failover, collision
 * resolution, continuations, etc.
 */
public enum GridClosureCallMode {
    /**
     * In this mode all closures will be executed on each node.
     * This mode essentially allows to <tt>broadcast</tt> executions to all nodes.
     */
    BROADCAST,

    /**
     * In this mode closures will be executed on the nodes provided by the default
     * load balancer.
     * <p>
     * NOTE: this mode <b>must</b> be used for all cache affinity routing. Load balance
     * manager has specific logic to handle co-location between compute grid jobs and
     * in-memory data grid data. All other modes will not work for affinity-based co-location.
     */
    BALANCE;

    /** Enumerated values. */
    private static final GridClosureCallMode[] VALS = values();

    /**
     * Efficiently gets enumerated value from its ordinal.
     *
     * @param ord Ordinal value.
     * @return Enumerated value.
     */
    @Nullable public static GridClosureCallMode fromOrdinal(byte ord) {
        return ord >= 0 && ord < VALS.length ? VALS[ord] : null;
    }
}

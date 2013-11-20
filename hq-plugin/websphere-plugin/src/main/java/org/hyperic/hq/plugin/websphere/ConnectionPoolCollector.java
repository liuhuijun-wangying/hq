/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of HQ.
 *
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */
package org.hyperic.hq.plugin.websphere;

import java.util.Set;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;

import javax.management.ObjectName;
import javax.management.j2ee.statistics.JDBCConnectionPoolStats;
import javax.management.j2ee.statistics.JDBCStats;

import org.hyperic.hq.product.PluginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ConnectionPoolCollector extends WebsphereCollector {

    private static final Log log = LogFactory.getLog(ConnectionPoolCollector.class.getName());
    private static final String[][] ATTRS = {
        //basic (default) PMI level
        {"CreateCount", "numCreates"},
        {"CloseCount", "numDestroys"},
        {"PoolSize", "poolSize"},
        {"FreePoolSize"}, //XXX
        {"WaitingThreadCount", "concurrentWaiters"},
        //non-default PMI level
        {"AllocateCount", "numAllocates"},
        {"ReturnCount", "numReturns"},
        {"PrepStmtCacheDiscardCount", "prepStmtCacheDiscards"},
        {"FaultCount", "faults"}
    };

    @Override
    protected ObjectName resolve(MBeanServerConnection server, ObjectName name) throws PluginException {
        Set beans;
        try {
            beans = server.queryNames(name, null);
        } catch (Exception e) {
            String msg = "resolve(" + name + "): " + e.getMessage();
            throw new PluginException(msg, e);
        }

        if (beans.size() != 1) {
            String msg = name + " query returned " + beans.size() + " results";
            throw new PluginException(msg);
        }
        return (ObjectName) beans.iterator().next();
    }

    protected void init(MBeanServerConnection mServer) throws PluginException {
        ObjectName name = newObjectNamePattern("type=JDBCProvider,"
                + "j2eeType=JDBCResource,"
                + "mbeanIdentifier=" + getModuleName() + ","
                + getProcessAttributes());
        setObjectName(resolve(mServer, name));
    }

    public void collect(MBeanServerConnection mServer) throws PluginException {
        JDBCStats stats = (JDBCStats) getStats(mServer, getObjectName());
        log.debug("[collect] stats=" + ((stats != null) ? "OK" : "KO"));
        if (stats != null) {
            JDBCConnectionPoolStats[] pools = stats.getConnectionPools();
            log.debug("[collect] pools.length=" + pools.length);
            collectStatCount(pools, ATTRS);
            setValue("npools", pools.length);
        } else {
            setValue("npools", 0);
        }
        setAvailability(true);
    }
}

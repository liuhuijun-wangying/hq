/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2004-2008], Hyperic, Inc.
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

package org.hyperic.hq.product.server.session;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.alerts.AlertDefinitionXmlParser;
import org.hyperic.hq.appdef.server.session.AppdefResourceType;
import org.hyperic.hq.appdef.server.session.CpropKey;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityValue;
import org.hyperic.hq.appdef.shared.CPropManager;
import org.hyperic.hq.appdef.shared.PlatformManager;
import org.hyperic.hq.appdef.shared.ServerManager;
import org.hyperic.hq.appdef.shared.ServiceManager;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.common.NotFoundException;
import org.hyperic.hq.common.VetoException;
import org.hyperic.hq.common.server.session.Audit;
import org.hyperic.hq.common.shared.AuditManager;
import org.hyperic.hq.context.Bootstrap;
import org.hyperic.hq.events.EventConstants;
import org.hyperic.hq.events.shared.AlertDefinitionManager;
import org.hyperic.hq.events.shared.AlertDefinitionValue;
import org.hyperic.hq.measurement.server.session.MonitorableType;
import org.hyperic.hq.measurement.shared.TemplateManager;
import org.hyperic.hq.product.MeasurementInfo;
import org.hyperic.hq.product.PlatformTypeInfo;
import org.hyperic.hq.product.Plugin;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.PluginInfo;
import org.hyperic.hq.product.PluginManager;
import org.hyperic.hq.product.PluginNotFoundException;
import org.hyperic.hq.product.PluginUpdater;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.hq.product.ProductPluginManager;
import org.hyperic.hq.product.ServerTypeInfo;
import org.hyperic.hq.product.ServiceType;
import org.hyperic.hq.product.ServiceTypeInfo;
import org.hyperic.hq.product.TypeInfo;
import org.hyperic.hq.product.pluginxml.PluginData;
import org.hyperic.hq.product.shared.PluginValue;
import org.hyperic.hq.product.shared.ProductManager;
import org.hyperic.util.config.ConfigOption;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.config.ConfigSchema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 */
@Service
public class ProductManagerImpl implements ProductManager {

    private Log log = LogFactory.getLog(ProductManagerImpl.class);

    private CPropManager cPropManager;
    private TemplateManager templateManager;
    private AuditManager auditManager;
    private PluginUpdater pluginUpdater = new PluginUpdater();
    private static final String ALERT_DEFINITIONS_XML_FILE = "etc/alert-definitions.xml";
    private AlertDefinitionManager alertDefinitionManager;
    private PluginDAO pluginDao;
    private PlatformManager platformManager;
    private ServerManager serverManager;
    private ServiceManager serviceManager;
    private AlertDefinitionXmlParser alertDefinitionXmlParser;
    private PluginAuditFactory pluginAuditFactory;

    @Autowired
    public ProductManagerImpl(PluginDAO pluginDao, AlertDefinitionManager alertDefinitionManager,
                              CPropManager cPropManager, TemplateManager templateManager, AuditManager auditManager,
                              ServerManager serverManager, ServiceManager serviceManager,
                              PlatformManager platformManager, AlertDefinitionXmlParser alertDefinitionXmlParser,
                              PluginAuditFactory pluginAuditFactory) {
        this.pluginDao = pluginDao;
        this.alertDefinitionManager = alertDefinitionManager;
        this.cPropManager = cPropManager;
        this.templateManager = templateManager;
        this.auditManager = auditManager;
        this.serverManager = serverManager;
        this.serviceManager = serviceManager;
        this.platformManager = platformManager;
        this.alertDefinitionXmlParser = alertDefinitionXmlParser;
        this.pluginAuditFactory = pluginAuditFactory;
    }

    /**
     * Update the appdef entities based on TypeInfo
     * 
     * 
     */
    private void updateAppdefEntities(String pluginName, TypeInfo[] entities) throws VetoException, NotFoundException {
        ArrayList<TypeInfo> platforms = new ArrayList<TypeInfo>();
        ArrayList<TypeInfo> servers = new ArrayList<TypeInfo>();
        ArrayList<TypeInfo> services = new ArrayList<TypeInfo>();

        // Organize the entity infos first
        for (int i = 0; i < entities.length; i++) {
            TypeInfo ei = entities[i];

            switch (ei.getType()) {
                case TypeInfo.TYPE_PLATFORM:
                    platforms.add(ei);
                    break;
                case TypeInfo.TYPE_SERVER:
                    servers.add(ei);
                    break;
                case TypeInfo.TYPE_SERVICE:
                    services.add(ei);
                    break;
                default:
                    break;
            }
        }

        // Update platforms
        if (platforms.size() > 0) {
            this.platformManager.updatePlatformTypes(pluginName, (PlatformTypeInfo[]) platforms
                .toArray(new PlatformTypeInfo[0]));
        }

        // Update servers
        if (servers.size() > 0) {
            serverManager.updateServerTypes(pluginName, (ServerTypeInfo[]) servers.toArray(new ServerTypeInfo[0]));
        }

        // Update services
        if (services.size() > 0) {
            serviceManager.updateServiceTypes(pluginName, (ServiceTypeInfo[]) services.toArray(new ServiceTypeInfo[0]));
        }
    }

    private ProductPluginManager getProductPluginManager() {
        return Bootstrap.getBean(ProductPluginDeployer.class).getProductPluginManager();
    }

    /**
     */
    public TypeInfo getTypeInfo(AppdefEntityValue value) throws PermissionException, AppdefEntityNotFoundException {
        return getProductPluginManager().getTypeInfo(value.getBasePlatformName(), value.getTypeName());
    }

    /**
     */
    public PluginManager getPluginManager(String type) throws PluginException {
        return getProductPluginManager().getPluginManager(type);
    }

    /**
     */
    // TODO: G
    public String getMonitoringHelp(AppdefEntityValue entityVal, Map<?, ?> props) throws PluginNotFoundException,
        PermissionException, AppdefEntityNotFoundException {
        TypeInfo info = getTypeInfo(entityVal);
        String help = getProductPluginManager().getMeasurementPluginManager().getHelp(info, props);
        if (help == null) {
            return null;
        }
        return help;
    }

    /**
     */
    public ConfigSchema getConfigSchema(String type, String name, AppdefEntityValue entityVal,
                                        ConfigResponse baseResponse) throws PluginException,
        AppdefEntityNotFoundException, PermissionException {

        PluginManager manager = getPluginManager(type);
        TypeInfo info = getTypeInfo(entityVal);
        return manager.getConfigSchema(name, info, baseResponse);
    }

    private void updatePlugin(PluginDAO plHome, PluginInfo pInfo) {
        Plugin plugin = plHome.findByName(pInfo.name);
        if (plugin == null) {
            plHome.create(pInfo.name, pInfo.jar, pInfo.md5);
        } else {
            plugin.setPath(pInfo.jar);
            plugin.setMD5(pInfo.md5);
        }
    }

    // e.g. in ~/.hq/plugin.properties
    // hq.plugins.system.forceUpdate=true
    private boolean forceUpdate(String plugin) {
        String key = ProductPluginManager.getPropertyKey(plugin, "forceUpdate");

        return "true".equals(getProductPluginManager().getProperties().getProperty(key));
    }

    private void pluginDeployed(PluginInfo pInfo) {
        // there is 1 hq-plugin.xml descriptor per-plugin which
        // contains metrics for all types supported by said plugin.
        // caching prevents reading/parsing the file for each type.
        // at this point we've got all the measurements for this plugin
        // so flush the cache to save some memory.
        // the file will be re-read/parsed when the plugin is redeployed.
        PluginData.deployed(pInfo.resourceLoader);
    }

    private boolean isVirtualServer(TypeInfo type) {
        if (type.getType() != TypeInfo.TYPE_SERVER) {
            return false;
        }
        return ((ServerTypeInfo) type).isVirtual();
    }

    /**
     */
    @Transactional
    public void deploymentNotify(String pluginName) throws PluginNotFoundException, VetoException, NotFoundException {
        ProductPlugin pplugin = (ProductPlugin) getProductPluginManager().getPlugin(pluginName);
        PluginValue pluginVal;
        PluginInfo pInfo;
        boolean created = false;
        long start = System.currentTimeMillis();

        pInfo = getProductPluginManager().getPluginInfo(pluginName);
        Plugin plugin = pluginDao.findByName(pluginName);
        pluginVal = plugin != null ? plugin.getPluginValue() : null;

        if (pluginVal != null && pInfo.name.equals(pluginVal.getName()) && pInfo.md5.equals(pluginVal.getMD5())) {
            log.info(pluginName + " plugin up to date");
            if (forceUpdate(pluginName)) {
                log.info(pluginName + " configured to force update");
            } else {
                pluginDeployed(pInfo);
                return;
            }
        } else {
            log.info(pluginName + " unknown -- registering");
            created = (pluginVal == null);
        }

        // Get the Appdef entities
        TypeInfo[] entities = pplugin.getTypes();
        if (entities == null) {
            log.info(pluginName + " does not define any resource types");
            updatePlugin(pluginDao, pInfo);
            if (created)
                pluginAuditFactory.deployAudit(pluginName, start, System.currentTimeMillis());
            else
                pluginAuditFactory.updateAudit(pluginName, start, System.currentTimeMillis());
            return;
        }

        Audit audit;
        boolean pushed = false;

        if (created) {
            audit = pluginAuditFactory.deployAudit(pluginName, start, start);
        } else {
            audit = pluginAuditFactory.updateAudit(pluginName, start, start);
        }

        try {
            auditManager.pushContainer(audit);
            pushed = true;
            updatePlugin(pluginName);
        } finally {
            if (pushed) {
                auditManager.popContainer(true);
            }
        }
    }

    /**
     * @param pluginName The name of the product plugin
     * @param serviceTypes The Set of {@link ServiceType}s to update
     * @throws PluginNotFoundException
     * @throws VetoException
     * @throws NotFoundException
     */
    @Transactional
    public void updateDynamicServiceTypePlugin(String pluginName, Set<ServiceType> serviceTypes)
        throws PluginNotFoundException, NotFoundException, VetoException {
        ProductPlugin productPlugin = (ProductPlugin) getProductPluginManager().getPlugin(pluginName);
        try {
            pluginUpdater.updateServiceTypes(productPlugin, serviceTypes);
            updatePlugin(pluginName);
        } catch (PluginException e) {
            log.error("Error updating service types.  Cause: " + e.getMessage());
        }
    }

    private void updatePlugin(String pluginName) throws VetoException, PluginNotFoundException, NotFoundException {
        ProductPlugin pplugin = (ProductPlugin) getProductPluginManager().getPlugin(pluginName);

        PluginInfo pInfo = getProductPluginManager().getPluginInfo(pluginName);

        TypeInfo[] entities = pplugin.getTypes();
        updateAppdefEntities(pluginName, entities);

        // Keep a list of templates to add
        // TODO: G (what are the parameters for the map returned by
        // TemplateManagerLocal.updateTemplates
        HashMap<MonitorableType, Map<?, MeasurementInfo>> toAdd = new HashMap<MonitorableType, Map<?, MeasurementInfo>>();

        for (int i = 0; i < entities.length; i++) {
            TypeInfo info = entities[i];

            MeasurementInfo[] measurements;

            try {
                measurements = getProductPluginManager().getMeasurementPluginManager().getMeasurements(info);
            } catch (PluginNotFoundException e) {
                if (!isVirtualServer(info)) {
                    log.info(info.getName() + " does not support measurement");
                }
                continue;
            }

            if (measurements != null && measurements.length > 0) {
                MonitorableType monitorableType = templateManager.getMonitorableType(pluginName, info);
                Map<?, MeasurementInfo> newMeasurements = templateManager.updateTemplates(pluginName, info,
                    monitorableType, measurements);
                toAdd.put(monitorableType, newMeasurements);
            }
        }

        // For performance reasons, we add all the new measurements at once.
        templateManager.createTemplates(pluginName, toAdd);

        // Add any custom properties.
        for (int i = 0; i < entities.length; i++) {
            TypeInfo info = entities[i];
            ConfigSchema schema = pplugin.getCustomPropertiesSchema(info);
            List<ConfigOption> options = schema.getOptions();
            AppdefResourceType appdefType = cPropManager.findResourceType(info);
            for (ConfigOption opt : options) {
                CpropKey c = cPropManager.findByKey(appdefType, opt.getName());
                if (c == null) {
                    cPropManager.addKey(appdefType, opt.getName(), opt.getDescription());
                }
            }
        }
        createAlertDefinitions(pInfo);
        pluginDeployed(pInfo);
        updatePlugin(pluginDao, pInfo);
    }

    private void createAlertDefinitions(final PluginInfo pInfo) throws VetoException {
        final InputStream alertDefns = pInfo.resourceLoader.getResourceAsStream(ALERT_DEFINITIONS_XML_FILE);
        if (alertDefns == null) {
            return;
        }
        try {
            final Set<AlertDefinitionValue> alertDefs = alertDefinitionXmlParser.parse(alertDefns);
            for (AlertDefinitionValue alertDefinition : alertDefs) {
                try {
                    final AppdefEntityID id = new AppdefEntityID(alertDefinition.getAppdefType(), alertDefinition
                        .getAppdefId());
                    final SortedMap<String, Integer> existingAlertDefinitions = alertDefinitionManager
                        .findAlertDefinitionNames(id, EventConstants.TYPE_ALERT_DEF_ID);
                    // TODO update existing alert defs - for now, just create if
                    // one does not exist. Be aware that this method is also
                    // called
                    // when new service type metadata is discovered (from
                    // updateServiceTypes method), as well as when a new or
                    // modified plugin jar is detected
                    if (!(existingAlertDefinitions.keySet().contains(alertDefinition.getName()))) {
                        alertDefinitionManager.createAlertDefinition(alertDefinition);
                    }
                } catch (Exception e) {
                    log.error("Unable to load some or all of alert definitions for plugin " + pInfo.name +
                              ".  Cause: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Unable to parse alert definitions for plugin " + pInfo.name + ".  Cause: " + e.getMessage());
        } finally {
            try {
                alertDefns.close();
            } catch (IOException e) {
                log.warn("Error closing InputStream to alert definitions file of plugin " + pInfo.name + ".  Cause: " +
                         e.getMessage());
            }
        }
    }
}
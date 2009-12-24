/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2007], Hyperic, Inc.
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
package org.hyperic.hq.hqu;

import java.io.File;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.application.HQApp;
import org.hyperic.hq.application.TransactionListener;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.context.Bootstrap;
import org.hyperic.hq.hqu.server.session.Attachment;
import org.hyperic.hq.hqu.server.session.UIPlugin;
import org.hyperic.hq.hqu.shared.UIPluginManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenditServerImpl implements RenditServer {
    public static final String PROP_PLUGIN_NAME   = "plugin.name";
    public static final String PROP_PLUGIN_VER    = "plugin.version";
    public static final String PROP_PLUGIN_APIMAJ = "plugin.apiMajor";
    public static final String PROP_PLUGIN_APIMIN = "plugin.apiMinor";
    
    private final Log log = LogFactory.getLog(RenditServerImpl.class);
    private final Object cfgLock = new Object();
    private Map<String, PluginWrapper>  plugins = new HashMap<String, PluginWrapper>();
    private File sysDir;
    private UIPluginManager uiPluginManager;
   
    private ClassLoader getUseLoader() {
        return RenditServerImpl.class.getClassLoader();
    }
    
    public File getSysDir() {
        synchronized (cfgLock) {
            return sysDir;
        }
    }
    
    private PluginWrapper getPlugin(String name) {
        PluginWrapper res;
        
        synchronized (cfgLock) {
            res = (PluginWrapper)plugins.get(name);
        }
        
        if (res == null) {
            throw new IllegalArgumentException("Unknown plugin [" + name + "]"); 
        }
        return res;
    }
    
    /**
     * Set the system directory which contains groovy support classes.
     * Since this object is a singleton, we rely on someone to call this
     * before they start adding plugins.
     */
    public void setSysDir(File sysDir) {
        synchronized (cfgLock) {
            if (!plugins.isEmpty()) {
                throw new IllegalStateException("Unable to set sysdir after " + 
                                                "plugins have been loaded");
            }
            this.sysDir = sysDir;
        }
    }
    
    @Transactional
    public void addPluginDir(File path) 
        throws Exception
    {
        log.info("Loading plugin from [" + path + "]");

        loadPlugin(path);
    }
    
    public void removePluginDir(String pluginName) {
        log.info("Removing plugin [" + pluginName + "]");
        synchronized (cfgLock) {
            plugins.remove(pluginName);
        }
    }

    /**
     * Loads a plugin into the rendit system, verifying the version numbers,
     * etc.
     *
     * @param path Path to the plugin
     */
    public PluginWrapper loadPlugin(final File path)
        throws PluginLoadException
    {
        try {
            return loadPluginInternal(path);
        } catch (Exception e) {
            throw new PluginLoadException(e.getMessage(), e);
        }
    }

    /**
     * Loads a plugin into the rendit system, verifying the version numbers,
     * etc.
     * 
     * @param path Path to the plugin
     */
    private PluginWrapper loadPluginInternal(final File path)
        throws PluginLoadException
    {
        final PluginWrapper plugin = new PluginWrapper(path, getSysDir(), 
                                                       getUseLoader());
        
        try {
            plugin.loadDispatcher();
        } catch(Exception e) {
            throw new PluginLoadException("Failed to load plugin", e);
        }
        
        Properties props;

        try {
            props = plugin.loadPlugin();
        } catch(PluginLoadException e) {
            throw e;
        } catch(Exception e) {
            throw new PluginLoadException("Error loading plugin at [" + 
                                          path.getAbsolutePath() + "]", e);
        }

        final String pluginName = props.getProperty(PROP_PLUGIN_NAME);
        final String pluginVer  = props.getProperty(PROP_PLUGIN_VER);
        
        log.info(pluginName + " version " + pluginVer + 
                  " loaded at [" + path.getName() + "]");
        deployPlugin(plugin, path, pluginName, pluginVer);
        return plugin;
    }
    
   
    private void deployPlugin(final PluginWrapper plugin, final File path, String pluginName, String pluginVer) {
        try {
        	// TODO Need to change this...this is done for short term to get past circular dependency w/ UIPluginManager
        	uiPluginManager = Bootstrap.getBean(UIPluginManager.class);
        	
            UIPlugin p = uiPluginManager.createOrUpdate(pluginName, pluginVer);
            plugin.deploy(p);
            HQApp.getInstance().addTransactionListener(
            new TransactionListener() {
                public void afterCommit(boolean success) {
                    if (success) {
                        synchronized (cfgLock) {
                            plugins.put(path.getName(), plugin);
                        }
                    }
                }
    
                public void beforeCommit() {
                }
            });
        } catch(Exception e) {
            throw new PluginLoadException("Error loading HQU plugin [" + 
                                          pluginName + "]", e);
        }
    }
    
    /**
     * Handles regular web requests for a UI plugin. 
     */
    public void handleRequest(String pluginName, RequestInvocationBindings b)
        throws Exception
    {
        PluginWrapper plugin = getPlugin(pluginName);
        plugin.handleRequest(b);
    }

    public AttachmentDescriptor getAttachmentDescriptor(String pluginName,
                                                        Attachment a,
                                                        Resource ent,
                                                        AuthzSubject u)
    {
        PluginWrapper plugin = getPlugin(pluginName);
        return plugin.getAttachmentDescriptor(a, ent, u);
    }

    public Object invokeMethod(String plugin, 
                               InvokeMethodInvocationBindings b)
        throws Exception
    {
        PluginWrapper p = getPlugin(plugin);
        return p.invokeMethod(b);
    }
    
    
    /**
     * Renders a template (.gsp file) to a Writer
     * This facility relies on a plugin being registered under the name
     * 'tmpl_render' which how to deal with a render invocation from the
     * dispatcher.
     * 
     * @params template The template to render
     * @params params   Local variables to pass to the template
     * @params output   Writer to render to
     */
    public void renderTemplate(File template, Map params, Writer output)
        throws Exception
    {
        PluginWrapper plugin = getPlugin("tmpl_render");
        List args = new ArrayList();
        
        if (plugin == null) {
            throw new SystemException("Required plugin [tmpl_render] not " + 
                                      "found");
        }
        
        args.add(template);
        args.add(params);
        args.add(output);
        InvokeMethodInvocationBindings b = 
            new InvokeMethodInvocationBindings("Renderer", "render", args);
                                               
        plugin.invokeMethod(b);
    }

    public static final RenditServer getInstance() {
        return Bootstrap.getBean(RenditServer.class);
    }
}
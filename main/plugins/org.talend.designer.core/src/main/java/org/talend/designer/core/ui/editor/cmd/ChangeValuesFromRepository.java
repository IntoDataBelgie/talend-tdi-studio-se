// ============================================================================
//
// Copyright (C) 2006-2021 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.core.ui.editor.cmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.MessageDialog;
import org.talend.commons.exception.ExceptionHandler;
import org.talend.commons.runtime.xml.XmlUtil;
import org.talend.commons.ui.gmf.util.DisplayUtils;
import org.talend.components.api.properties.ComponentProperties;
import org.talend.core.GlobalServiceRegister;
import org.talend.core.ITDQPatternService;
import org.talend.core.database.conn.version.EDatabaseVersion4Drivers;
import org.talend.core.hadoop.HadoopConstants;
import org.talend.core.model.components.ComponentCategory;
import org.talend.core.model.components.IODataComponent;
import org.talend.core.model.context.ContextUtils;
import org.talend.core.model.metadata.IMetadataTable;
import org.talend.core.model.metadata.MetadataToolHelper;
import org.talend.core.model.metadata.QueryUtil;
import org.talend.core.model.metadata.builder.connection.Connection;
import org.talend.core.model.metadata.builder.connection.DatabaseConnection;
import org.talend.core.model.metadata.builder.connection.FTPConnection;
import org.talend.core.model.metadata.builder.connection.HL7Connection;
import org.talend.core.model.metadata.builder.connection.MDMConnection;
import org.talend.core.model.metadata.builder.connection.MetadataColumn;
import org.talend.core.model.metadata.builder.connection.MetadataTable;
import org.talend.core.model.metadata.builder.connection.Query;
import org.talend.core.model.metadata.builder.connection.SAPConnection;
import org.talend.core.model.metadata.builder.connection.SalesforceModuleUnit;
import org.talend.core.model.metadata.builder.connection.SalesforceSchemaConnection;
import org.talend.core.model.metadata.builder.connection.TacokitDatabaseConnection;
import org.talend.core.model.metadata.builder.connection.WSDLSchemaConnection;
import org.talend.core.model.metadata.builder.connection.XmlFileConnection;
import org.talend.core.model.metadata.builder.connection.impl.XmlFileConnectionImpl;
import org.talend.core.model.metadata.designerproperties.PropertyConstants.CDCTypeMode;
import org.talend.core.model.metadata.designerproperties.RepositoryToComponentProperty;
import org.talend.core.model.process.EConnectionType;
import org.talend.core.model.process.EParameterFieldType;
import org.talend.core.model.process.IConnectionCategory;
import org.talend.core.model.process.IElement;
import org.talend.core.model.process.IElementParameter;
import org.talend.core.model.process.INode;
import org.talend.core.model.process.INodeConnector;
import org.talend.core.model.process.IProcess;
import org.talend.core.model.properties.ConnectionItem;
import org.talend.core.model.properties.ContextItem;
import org.talend.core.model.properties.Item;
import org.talend.core.model.properties.SAPConnectionItem;
import org.talend.core.model.repository.ERepositoryObjectType;
import org.talend.core.model.utils.TalendTextUtils;
import org.talend.core.runtime.services.IGenericWizardService;
import org.talend.core.service.IJsonFileService;
import org.talend.core.utils.TalendQuoteUtils;
import org.talend.cwm.helper.ConnectionHelper;
import org.talend.cwm.helper.TaggedValueHelper;
import org.talend.designer.core.i18n.Messages;
import org.talend.designer.core.model.components.EParameterName;
import org.talend.designer.core.model.components.EmfComponent;
import org.talend.designer.core.model.components.Expression;
import org.talend.designer.core.model.process.jobsettings.JobSettingsConstants;
import org.talend.designer.core.model.utils.emf.talendfile.ContextType;
import org.talend.designer.core.ui.editor.nodes.Node;
import org.talend.designer.core.ui.editor.process.Process;
import org.talend.designer.core.ui.editor.process.ProcessUpdateManager;
import org.talend.designer.core.ui.preferences.StatsAndLogsConstants;
import org.talend.designer.core.ui.projectsetting.ImplicitContextLoadElement;
import org.talend.designer.core.ui.views.jobsettings.JobSettings;
import org.talend.designer.core.utils.ConnectionUtil;
import org.talend.designer.core.utils.DesignerUtilities;
import org.talend.designer.core.utils.JobSettingVersionUtil;
import org.talend.designer.core.utils.SAPParametersUtils;
import org.talend.designer.core.utils.UpdateParameterUtils;
import org.talend.metadata.managment.ui.utils.ConnectionContextHelper;
import org.talend.repository.UpdateRepositoryUtils;
import org.talend.repository.model.IRepositoryNode.EProperties;

/**
 * DOC nrousseau class global comment. Detailled comment <br/>
 *
 * $Id$
 *
 */
public class ChangeValuesFromRepository extends ChangeMetadataCommand {

    private final Map<String, Object> oldValues;

    private final IElement elem;

    private final Connection connection;

    private final String value;

    private final String propertyName;

    private String oldMetadata;

    private boolean isGuessQuery = false;

    private final String propertyTypeName;

    private final String updataComponentParamName;

    // use for SAP
    private String sapFunctionLabel = null;

    private String sapIDocLabel = null;

    // for jobtemplate plugin(true, bug 5198)
    private boolean ignoreContextMode = false;

    private boolean dragAndDropAction = false;

    private boolean reOpenXSDBool = false;

    private int index;

    private String currentTableName;

    private IMetadataTable table;

    private SalesforceModuleUnit moduleUnit;

    private boolean toUpdate;

    public ChangeValuesFromRepository(IElement elem, Connection connection, String propertyName, String value) {
        this.elem = elem;
        this.connection = connection;
        this.value = value;
        this.propertyName = propertyName;
        oldValues = new HashMap<String, Object>();
        setLabel(Messages.getString("PropertyChangeCommand.Label")); //$NON-NLS-1$
        propertyTypeName = EParameterName.PROPERTY_TYPE.getName();
        EParameterName.REPOSITORY_PROPERTY_TYPE.getName();
        updataComponentParamName = EParameterName.UPDATE_COMPONENTS.getName();
    }

    public ChangeValuesFromRepository(IElement elem, Connection connection, String propertyName, String value, int index,
            boolean isNotSim) {
        this.elem = elem;
        this.connection = connection;
        this.value = value;
        this.propertyName = propertyName;
        this.index = index;
        this.isNotSim = isNotSim;
        oldValues = new HashMap<String, Object>();
        setLabel(Messages.getString("PropertyChangeCommand.Label")); //$NON-NLS-1$
        propertyTypeName = EParameterName.PROPERTY_TYPE.getName();
        EParameterName.REPOSITORY_PROPERTY_TYPE.getName();
        updataComponentParamName = EParameterName.UPDATE_COMPONENTS.getName();
    }

    public ChangeValuesFromRepository(IElement elem, Connection connection, String propertyName, String value,
            boolean reOpenXSDBool) {
        this.reOpenXSDBool = reOpenXSDBool;
        this.elem = elem;
        this.connection = connection;
        this.value = value;
        this.propertyName = propertyName;
        oldValues = new HashMap<String, Object>();
        if (connection instanceof XmlFileConnection) {
            if (XmlUtil.isXSDFile(TalendQuoteUtils.removeQuotes(((XmlFileConnection) connection).getXmlFilePath()))) {
                dragAndDropAction = true;
            }
        }
        setLabel(Messages.getString("PropertyChangeCommand.Label")); //$NON-NLS-1$
        propertyTypeName = EParameterName.PROPERTY_TYPE.getName();
        EParameterName.REPOSITORY_PROPERTY_TYPE.getName();
        updataComponentParamName = EParameterName.UPDATE_COMPONENTS.getName();
    }

    public ChangeValuesFromRepository(IElement elem, Connection connection, IMetadataTable table, String propertyName,
            String value, boolean reOpenXSDBool) {
        this(elem, connection, propertyName, value, reOpenXSDBool);
        this.table = table;
    }

    public ChangeValuesFromRepository(IElement elem, Connection connection, IMetadataTable table, String propertyName,
            String value) {
        this(elem, connection, propertyName, value);
        this.table = table;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute() {

        // Force redraw of Commponents propoerties
        elem.setPropertyValue(updataComponentParamName, new Boolean(true));

        boolean allowAutoSwitch = true;

        IElementParameter elemParam = elem.getElementParameter(EParameterName.REPOSITORY_ALLOW_AUTO_SWITCH.getName());
        if (elemParam != null) {
            // add for TDI-8053
            elemParam.setValue(Boolean.FALSE);
            allowAutoSwitch = (Boolean) elemParam.getValue();
        }
        if (!allowAutoSwitch && (elem instanceof Node)) {
            // force the autoSwitch to true if the schema is empty and if the
            // query is not set.
            Node node = (Node) elem;
            boolean isSchemaEmpty = false;
            if (node.getMetadataList().size() > 0) {
                isSchemaEmpty = node.getMetadataList().get(0).getListColumns().size() == 0;
            } else {
                isSchemaEmpty = true;
            }

            if (isSchemaEmpty) {
                allowAutoSwitch = true;
            }
            if (((INode) elem).getComponent().getName().equals("tWebService")) { //$NON-NLS-1$
                allowAutoSwitch = true;
            }
        }

        if (propertyName.split(":")[1].equals(propertyTypeName)) { //$NON-NLS-1$
            elem.setPropertyValue(propertyName, value);
            if (allowAutoSwitch) {
                // Update spark mode to first default value available
                if (elem instanceof IProcess) {
                    if (ComponentCategory.CATEGORY_4_SPARK.getName().equals(((IProcess) elem).getComponentsType())
                            || ComponentCategory.CATEGORY_4_SPARKSTREAMING.getName()
                                    .equals(((IProcess) elem).getComponentsType())) {
                        if (EmfComponent.REPOSITORY.equals(value)) {
                            IElementParameter sparkParam = ((IProcess) elem).getElementParameter(HadoopConstants.SPARK_MODE);
                            List<? extends IElementParameter> listParam = elem.getElementParameters();
                            for (int i = 0; i < sparkParam.getListItemsShowIf().length; i++) {
                            	if (Expression.evaluate(sparkParam.getListItemsShowIf()[i], listParam)) {
                            		sparkParam.setValue(sparkParam.getListItemsValue()[i]);
                            		break;
                            	}
                            }
                        }
                    }
                }
                setOtherProperties();
            }
        } else {
            oldMetadata = (String) elem.getPropertyValue(propertyName);
            elem.setPropertyValue(propertyName, value);
            if (allowAutoSwitch) {
                setOtherProperties();
            }
        }

        String propertyParamName = null;
        if (elem.getElementParameter(propertyName).getParentParameter() != null) {
            IElementParameter param = elem.getElementParameter(propertyName).getParentParameter();
            if (param.getFieldType() == EParameterFieldType.PROPERTY_TYPE) {
                propertyParamName = param.getName();
            }
        }

        if (propertyName.split(":")[1].equals(propertyTypeName) && (EmfComponent.BUILTIN.equals(value))) { //$NON-NLS-1$
            for (IElementParameter param : elem.getElementParameters()) {
                if (param.getRepositoryProperty() != null && !param.getRepositoryProperty().equals(propertyParamName)) {
                    continue;
                }
                boolean paramFlag = JobSettingsConstants.isExtraParameter(param.getName());
                boolean extraFlag = JobSettingsConstants.isExtraParameter(propertyName.split(":")[0]); //$NON-NLS-1$
                if (paramFlag == extraFlag) {
                    // for memo sql
                    if (param.getFieldType() == EParameterFieldType.MEMO_SQL) {
                        IElementParameter querystoreParam = elem.getElementParameterFromField(EParameterFieldType.QUERYSTORE_TYPE,
                                param.getCategory());
                        if (querystoreParam != null) {
                            Map<String, IElementParameter> childParam = querystoreParam.getChildParameters();
                            if (childParam != null) {
                                IElementParameter queryTypeParam = childParam.get(EParameterName.QUERYSTORE_TYPE.getName());
                                if (queryTypeParam != null && EmfComponent.REPOSITORY.equals(queryTypeParam.getValue())) {
                                    continue;
                                }
                            }
                        }
                    }
                    if (param.calcRepositoryValue() != null) {
                        param.setReadOnly(false);
                        // for job settings extra.(feature 2710)
                        param.setRepositoryValueUsed(false);
                    }
                }

            }
        } else {
            oldValues.clear();
            List<ComponentProperties> componentProperties = null;
            IGenericWizardService wizardService = null;
            if (GlobalServiceRegister.getDefault().isServiceRegistered(IGenericWizardService.class)) {
                wizardService = GlobalServiceRegister.getDefault()
                        .getService(IGenericWizardService.class);
            }
            if (wizardService != null && wizardService.isGenericConnection(connection)) {
                componentProperties = wizardService.getAllComponentProperties(connection, null, true);
            }
            IElementParameter propertyParam = elem.getElementParameter(propertyName);
            List<IElementParameter> elementParameters = new ArrayList<>(elem.getElementParameters());

            // (bug 5198)
            IElementParameter parentParameter = propertyParam.getParentParameter();
            if (parentParameter != null) {
                IElementParameter param = parentParameter.getChildParameters()
                        .get(EParameterName.REPOSITORY_PROPERTY_TYPE.getName());
                if (param != null && propertyParam == param) { // avoid to process twice.
                    ConnectionItem connItem = UpdateRepositoryUtils.getConnectionItemByItemId((String) param.getValue());
                    if (connItem != null) {
                        if (elem instanceof Node) {
                            ConnectionContextHelper.addContextForNodeParameter((Node) elem, connItem, ignoreContextMode);
                        } else if (elem instanceof Process) {
                            ConnectionContextHelper.addContextForProcessParameter((Process) elem, connItem, param.getCategory(),
                                    ignoreContextMode);
                        }
                    }
                }
            }
            Map<Object, Object> contextMap = new HashMap<Object, Object>();
            if (elem instanceof INode) {
                contextMap.put("NODE", elem);
            }

            for (IElementParameter param : elementParameters) { // elementParameters: target component's element

                String repositoryValue = param.calcRepositoryValue();
                if (param.getFieldType() == EParameterFieldType.PROPERTY_TYPE) {
                    continue;
                }
                boolean isGenericRepositoryValue = RepositoryToComponentProperty.isGenericRepositoryValue(connection,
                        componentProperties, param.getName());
                
                String newRepValue = param.getName();
                String newParamName;
                if (elem instanceof ImplicitContextLoadElement) {
                    newParamName = getParamNameForImplicitContext(param);
                } else {
                    newParamName = getParamNameForOldJDBC(param);
                }
                if (connection instanceof TacokitDatabaseConnection) {
                    newParamName = getParamNameForTacokitDatabaseConnection(param);
                }
                boolean isJDBCRepValue = false;
                if (!isGenericRepositoryValue && newParamName != null) {
                    isJDBCRepValue = RepositoryToComponentProperty.isGenericRepositoryValue(connection, componentProperties,
                            newParamName);
                    newRepValue = newParamName;
                }
                if ((repositoryValue == null && isGenericRepositoryValue) || isJDBCRepValue) {
                    repositoryValue = newRepValue;
                    param.setRepositoryValue(repositoryValue);
                    param.setRepositoryValueUsed(true);
                }
                if (!isJDBCRepValue && connection instanceof DatabaseConnection
                        && EParameterFieldType.MAPPING_TYPE.equals(param.getFieldType())
                        && StringUtils.equals(EParameterName.MAPPING.getName(), param.getName())) {// $NON-NLS-1$
                    repositoryValue = param.getName();
                }
                if (repositoryValue == null
                        || param.getRepositoryProperty() != null && !param.getRepositoryProperty().equals(propertyParamName)) {
                    param.setRepositoryValueUsed(false);
                    try {
                        param.setReadOnly(false);// reset the readonly flag first
                        param.setReadOnly(param.isReadOnly(param.getElement().getElementParameters()));
                    } catch (Throwable e) {
                        ExceptionHandler.process(e);
                        param.setReadOnly(false);
                    }
                    continue;
                }
                String componentName = elem instanceof INode ? (((INode) elem).getComponent().getName()) : null;
                boolean b = elem instanceof INode && (((INode) elem).getComponent().getName().equals("tHL7Input") //$NON-NLS-1$
                        || ((INode) elem).getComponent().getName().equals("tAdvancedFileOutputXML") //$NON-NLS-1$
                        || ((INode) elem).getComponent().getName().equals("tMDMOutput")
                        || ((INode) elem).getComponent().getName().equals("tWebService")
                        || ((INode) elem).getComponent().getName().equals("tCreateTable")
                        || ((INode) elem).getComponent().getName().equals("tWriteJSONField")); //$NON-NLS-1$

                if ((repositoryValue != null || isGenericRepositoryValue || b) && (!param.getName().equals(propertyTypeName))) {
                    if (param.getRepositoryProperty() != null && !param.getRepositoryProperty().equals(propertyParamName)) {
                        continue;
                    }
                    Object objectValue = null;
                    if (connection instanceof XmlFileConnection && this.dragAndDropAction == true
                            && repositoryValue.equals("FILE_PATH") && reOpenXSDBool == true) {

                        objectValue = RepositoryToComponentProperty.getXmlAndXSDFileValue((XmlFileConnection) connection,
                                repositoryValue);
                    } else if ("module.moduleName".equals(repositoryValue)) {
                        objectValue = null;
                        param.setRepositoryValueUsed(false);
                    } else if (connection instanceof SalesforceSchemaConnection && "MODULENAME".equals(repositoryValue)) { //$NON-NLS-1$
                        /*
                         * SalesforceSchemaConnection is no longer available with new TCOMP Wizard(?). In debug,
                         * connection is GenericConnectionImpl. "MODULENAME" is also unavailable, since it has to be
                         * module.moduleName. The same situation with "CUSTOM_MODULE_NAME". Feel free to remove this
                         * code, or improve it by fixing SalesforceSchemaConnection usage and corrected repositoryValue.
                         */
                        if (this.moduleUnit != null) {
                            objectValue = moduleUnit.getModuleName();
                        } else {
                            objectValue = null;
                        }
                    } // for bug TDI-8662 . should be careful that connection.getModuleName() will always get the latest
                      // name of the
                      // module which was the last one be retrived
                    else if (connection instanceof SalesforceSchemaConnection && "CUSTOM_MODULE_NAME".equals(repositoryValue)) { //$NON-NLS-1$
                        if (this.moduleUnit != null) {
                            objectValue = moduleUnit.getModuleName();
                        } else {
                            objectValue = null;
                        }
                    } else if ("table.tableName".equals(repositoryValue)) {
                        objectValue = null;
                        param.setRepositoryValueUsed(false);
                    } else if (connection instanceof MDMConnection) {
                        if (table == null) {
                            IMetadataTable metaTable = null;
                            if (((Node) elem).getMetadataList().size() > 0) {
                                metaTable = ((Node) elem).getMetadataList().get(0);
                            }
                            objectValue = RepositoryToComponentProperty.getValue(connection, repositoryValue, metaTable);
                        } else {
                            objectValue = RepositoryToComponentProperty.getValue(connection, repositoryValue, table);
                        }
                    } else if (connection instanceof WSDLSchemaConnection && "USE_PROXY".equals(repositoryValue)) { //$NON-NLS-1$
                        objectValue = ((WSDLSchemaConnection) connection).isUseProxy();
                    } else {
                        IMetadataTable metaTable = table;
                        if (metaTable == null && elem instanceof Node) {
                            contextMap.put("NODE", elem);
                            INodeConnector conn = ((Node) elem).getConnectorFromType(EConnectionType.FLOW_MAIN);
                            if (conn != null && conn.getMaxLinkOutput() == 1) {
                                metaTable = ((Node) elem).getMetadataFromConnector(conn.getName());
                            }
                        }
                        objectValue = RepositoryToComponentProperty.getValue(connection, repositoryValue, metaTable,
                                componentName, contextMap);
                    }

                    if (GlobalServiceRegister.getDefault().isServiceRegistered(IJsonFileService.class)) {
                        IJsonFileService jsonService = GlobalServiceRegister.getDefault()
                                .getService(IJsonFileService.class);
                        boolean paramChanged = jsonService.changeFilePathFromRepository(connection, param, elem, objectValue);
                        if (paramChanged) {
                            continue;
                        }
                    }

                    String repoValue = param.calcRepositoryValue();
                    if (objectValue != null) {
                        oldValues.put(param.getName(), param.getValue()); 
                        
                        if (!objectValue.equals("\"\"") && repositoryValue.equals("PASSWORD") && elem instanceof Node
                                && ((INode) elem).getComponent().getName().equals("cSQLConnection")) {
                            elem.setPropertyValue("AUTH_REQUIRED", true);
                        }
                        
                        if (param.getFieldType().equals(EParameterFieldType.CLOSED_LIST)
                                && "TYPE".equals(repoValue)) { //$NON-NLS-1$
                            String dbVersion = "";
                            if (connection instanceof DatabaseConnection) {
                                dbVersion = ((DatabaseConnection) connection).getDbVersionString();
                            }
                            boolean found = false;
                            String[] list = param.getListRepositoryItems();
                            for (int i = 0; (i < list.length) && (!found); i++) {
                                if (objectValue.equals(list[i])) {
                                    found = true;
                                    elem.setPropertyValue(param.getName(), param.getListItemsValue()[i]);
                                }
                            }

                            IElementParameter elementParameter = null;
                            IElementParameter elementParameter2 = null;
                            if (EParameterName.DB_TYPE.getName().equals(param.getName())) {
                                elementParameter = elem.getElementParameter(EParameterName.DB_VERSION.getName());
                                elementParameter2 = elem.getElementParameter(EParameterName.SCHEMA_DB.getName());
                            } else {
                                elementParameter = elem.getElementParameter(
                                        JobSettingsConstants.getExtraParameterName(EParameterName.DB_VERSION.getName()));
                                elementParameter2 = elem.getElementParameter(
                                        JobSettingsConstants.getExtraParameterName(EParameterName.SCHEMA_DB.getName()));
                            }
                            String dbType = "";
                            if (param.getValue() != null) {
                                int indexOfItemFromList = param.getIndexOfItemFromList(param.getValue().toString());
                                if (indexOfItemFromList != -1) {
                                    dbType = param.getListItemsDisplayCodeName()[indexOfItemFromList];
                                }
                            }
                            // Some DB not need fill the schema parameter for the JobSetting View "Extra" ,"Stats&Logs"
                            if (elementParameter2 != null && !elementParameter2.isShow(elem.getElementParameters())
                                    && !elementParameter2.getValue().equals("")) {
                                elementParameter2.setValue("");
                            }
                            if (StatsAndLogsConstants.JDBC.equals(dbType)) {
                                IElementParameter dbNameParm = elem.getElementParameter(EParameterName.DBNAME.getName());
                                if (dbNameParm != null) {
                                    dbNameParm.setValue("");
                                }
                            } else {
                                IElementParameter rulParam = elem.getElementParameter(EParameterName.URL.getName());
                                if (rulParam != null) {
                                    rulParam.setValue("");
                                }
                                IElementParameter classParam = elem.getElementParameter(EParameterName.DRIVER_CLASS.getName());
                                if (classParam != null) {
                                    classParam.setValue("");
                                }
                                IElementParameter jarParam = elem.getElementParameter(EParameterName.DRIVER_JAR.getName());
                                if (jarParam != null) {
                                    jarParam.setValue(new ArrayList<Map<String, Object>>());
                                }
                            }

                            JobSettingVersionUtil.setDbVersion(elementParameter, dbType, dbVersion, false);
                            DesignerUtilities.setSchemaDB(elementParameter2, param.getValue());
                        } else if (param.getFieldType().equals(EParameterFieldType.CLOSED_LIST)
                                && "FRAMEWORK_TYPE".equals(repoValue)) { //$NON-NLS-1$
                            String[] list = param.getListItemsDisplayName();
                            for (int i = 0; i < list.length; i++) {
                                if (objectValue.equals(list[i])) {
                                    elem.setPropertyValue(param.getName(), param.getListItemsValue()[i]);
                                }
                            }
                        } else if (param.getFieldType().equals(EParameterFieldType.CLOSED_LIST)
                                && "EDI_VERSION".equals(repoValue)) {
                            String[] list = param.getListItemsDisplayName();
                            for (String element : list) {
                                if (objectValue.toString().toUpperCase().equals(element)) {
                                    elem.setPropertyValue(param.getName(), objectValue);
                                }
                            }
                        } else if (param.getFieldType().equals(EParameterFieldType.CLOSED_LIST)
                                && "DRIVER".equals(repoValue)) {
                            String[] list = param.getListItemsDisplayCodeName();
                            for (String element : list) {
                                if (objectValue.toString().toUpperCase().equals(element)) {
                                    elem.setPropertyValue(param.getName(), objectValue);
                                }
                            }
                        } else if (param.getFieldType().equals(EParameterFieldType.CLOSED_LIST)
                                && "CONNECTION_MODE".equals(repoValue)) {//$NON-NLS-1$
                            if (!objectValue.equals(param.getValue())) {
                                PropertyChangeCommand cmd = new PropertyChangeCommand(elem, "CONNECTION_MODE", objectValue);//$NON-NLS-1$
                                cmd.execute();
                            }
                        } else {
                            if (repositoryValue.equals("ENCODING")) { //$NON-NLS-1$
                                IElementParameter paramEncoding = param.getChildParameters()
                                        .get(EParameterName.ENCODING_TYPE.getName());
                                if (connection instanceof FTPConnection) {
                                    if (((FTPConnection) connection).getEcoding() != null) {
                                        paramEncoding.setValue(((FTPConnection) connection).getEcoding());
                                    } else {
                                        paramEncoding.setValue(EmfComponent.ENCODING_TYPE_CUSTOM);
                                    }

                                } else {
                                    if (objectValue instanceof String) {
                                        String str = TalendTextUtils.removeQuotes((String) objectValue);
                                        if (str.equals(EmfComponent.ENCODING_TYPE_UTF_8)) {
                                            paramEncoding.setValue(EmfComponent.ENCODING_TYPE_UTF_8);
                                        } else if (str.equals(EmfComponent.ENCODING_TYPE_ISO_8859_15)) {
                                            paramEncoding.setValue(EmfComponent.ENCODING_TYPE_ISO_8859_15);
                                        } else {
                                            paramEncoding.setValue(EmfComponent.ENCODING_TYPE_CUSTOM);
                                            // paramEncoding.setRepositoryValueUsed(true);
                                        }
                                    }
                                }

                            } else if (repositoryValue.equals("CSV_OPTION")) { //$NON-NLS-1$
                                setOtherProperties();
                            }

                            if (repositoryValue.equals("MODULENAME")) {//$NON-NLS-1$
                                List list = new ArrayList();
                                Object[] listItemsValue = elem.getElementParameter("MODULENAME").getListItemsValue();
                                for (Object element : listItemsValue) {
                                    list.add(element);
                                }
                                if (list != null && !list.contains(objectValue)) {
                                    objectValue = "CustomModule";//$NON-NLS-1$
                                }
                            }

                            // hywang add for excel 2007
                            if (repositoryValue.equals(EParameterName.FILE_PATH.getName())) {
                                String filePath = "";
                                if (connection.isContextMode()) {
                                    ContextItem contextItem = ContextUtils.getContextItemById2(connection.getContextId());

                                    if (contextItem != null) {
                                        String selectedContext = contextItem.getDefaultContext();
                                        final ContextType contextTypeByName = ContextUtils.getContextTypeByName(contextItem,
                                                selectedContext, true);
                                        filePath = ConnectionContextHelper.getOriginalValue(contextTypeByName,
                                                objectValue.toString());
                                    }
                                } else {

                                    filePath = TalendTextUtils.removeQuotes(objectValue.toString());
                                }
                                boolean versionCheckFor2007 = false;
                                if (filePath != null && filePath.endsWith(".xlsx")) {
                                    versionCheckFor2007 = true;
                                }
                                if (elem.getElementParameter("VERSION_2007") != null) {
                                    elem.setPropertyValue("VERSION_2007", versionCheckFor2007);
                                }
                            }
                            if (param.getFieldType().equals(EParameterFieldType.FILE)) {
                                if (objectValue != null) {
                                    objectValue = objectValue.toString().replace("\\", "/");
                                }
                            }

                            if ((isJDBCRepValue && param.getName().equals(EParameterName.DRIVER_JAR.getName())) || param.getName()
                                    .equals(JobSettingsConstants.getExtraParameterName(EParameterName.DRIVER_JAR.getName()))) {
                                objectValue = ConnectionUtil.extractDriverValue(param, objectValue);
                            }

                            /**
                             * For tCreateTable, several parameter have the same repository value
                             * (e.g.DB_MYSQL_VERSION/DB_POSTGRE_VERSION/...=>DB_VERSION), avoid to set all...
                             */
                            if (EParameterName.DB_VERSION.getName().equals(repoValue)
                                    && !param.isShow(elem.getElementParameters())) {
                                continue;
                            }

                            if (EParameterName.PROPERTIES.getName().equals(param.getName())
                                    && StringUtils.isNotBlank(objectValue.toString())
                                    && objectValue.toString().contains("authentication=ActiveDirectoryPassword")) {//$NON-NLS-1$
                                if (connection instanceof DatabaseConnection) {
                                    String dbVersion = ((DatabaseConnection) connection).getDbVersionString();
                                    if (EDatabaseVersion4Drivers.MSSQL_PROP.name().equals(dbVersion)) {
                                        elem.setPropertyValue("ACTIVE_DIR_AUTH", true);//$NON-NLS-1$
                                    }
                                }
                            }

                            elem.setPropertyValue(param.getName(), objectValue);
                        }
                        if (elem instanceof Node) {
                            if (!ProcessUpdateManager.isIgnoreJDBCRepositoryParameter((Node)elem, param.getName())) {
                                boolean update = true;
                                if (connection instanceof TacokitDatabaseConnection) {
                                    update = RepositoryToComponentProperty.isGenericRepositoryValue(connection, null,
                                            param.getName());
                                }
                                param.setRepositoryValueUsed(update);
                            }
                        } else {
                            param.setRepositoryValueUsed(true);
                        }
                    } else if (EParameterFieldType.TABLE.equals(param.getFieldType())
                            && "XML_MAPPING".equals(repoValue)) { //$NON-NLS-1$

                        List<Map<String, Object>> table = (List<Map<String, Object>>) elem.getPropertyValue(param.getName());
                        if (((Node) elem).getMetadataList().size() > 0) {
                            IMetadataTable metaTable = ((Node) elem).getMetadataList().get(0);
                            RepositoryToComponentProperty.getTableXmlFileValue(connection, "XML_MAPPING", param, //$NON-NLS-1$
                                    table, metaTable);
                            param.setRepositoryValueUsed(true);
                        }
                    } else if (param.getFieldType().equals(EParameterFieldType.TABLE)
                            && "WSDL_PARAMS".equals(repoValue) && connection != null) { //$NON-NLS-1$
                        List<Map<String, Object>> table = (List<Map<String, Object>>) elem.getPropertyValue(param.getName());
                        table.clear();
                        ArrayList parameters = ((WSDLSchemaConnection) connection).getParameters();
                        if (parameters != null) {
                            for (Object object : parameters) {
                                Map<String, Object> map2 = new HashMap<String, Object>();
                                map2.put("VALUE", TalendTextUtils.addQuotes(object.toString())); //$NON-NLS-1$
                                table.add(map2);
                            }
                        }
                        param.setRepositoryValueUsed(true);
                    } else if (param.getFieldType().equals(EParameterFieldType.TEXT)
                            && "XPATH_QUERY".equals(repoValue)) { //$NON-NLS-1$
                        param.setRepositoryValueUsed(true);
                    } else {
                        // For SAP
                        String paramName = param.getName();
                        if ("SAP_PROPERTIES".equals(paramName) || "MAPPING_INPUT".equals(paramName)
                                || "SAP_FUNCTION".equals(paramName) // INPUT_PARAMS should be MAPPING_INPUT,bug16426
                                || "OUTPUT_PARAMS".equals(paramName) || "SAP_ITERATE_OUT_TYPE".equals(paramName)
                                || "SAP_ITERATE_OUT_TABLENAME".equals(paramName)) {
                            SAPParametersUtils.retrieveSAPParams(elem, connection, param, getSapFunctionLabel());
                        }
                        if ("GATEWAYSERVICE".equals(paramName) || "PROGRAMID".equals(paramName) || "FORMAT_XML".equals(paramName)
                                || "FILE_IDOC_XML".equals(paramName) || "FORMAT_HTML".equals(paramName)
                                || "FILE_IDOC_HTML".equals(paramName)) {
                            SAPParametersUtils.getSAPIDocParams(elem, connection, param, getSapIDocLabel());
                        }
                    }

                    if (param.isRepositoryValueUsed()) {
                        if (("GENERATION_MODE").equals(param.getName())) {
                            param.setReadOnly(true);
                        } else if (isJDBCRepValue && EParameterFieldType.MAPPING_TYPE.equals(param.getFieldType())
                                && EParameterName.MAPPING.getName().equals(param.getName())) {
                            param.setReadOnly(true);
                        } else {
                            param.setReadOnly(false);
                        }
                    }
                }
            }//  for (IElementParameter param : elementParameters): end
        }
        toUpdate = false;
        // change AS400 value
        for (IElementParameter curParam : elem.getElementParameters()) {
            if (curParam.getFieldType().equals(EParameterFieldType.AS400_CHECK)) {
                setOtherProperties();
            }
            // change the HL7 Version
            if (connection instanceof HL7Connection) {
                if (curParam.getName().equals("HL7_VER")) {
                    String hl7VersionString = connection.getVersion();
                    if (hl7VersionString != null) {
                        hl7VersionString = hl7VersionString.replace(".", "");
                        curParam.setValue(hl7VersionString);
                    }
                }
            }
            UpdateParameterUtils.setDefaultValues(curParam, elem);
        }

        // set CDS View Parameter when DND to existing tSAPTableInput node
        if (!allowAutoSwitch && elem.getElementParameter("CDS_VIEWS") != null) {
            setOtherProperties();
        }

        if (elem instanceof Node) {
            // Xstream Cdc Type Mode
            boolean isXstreamCdcTypeMode = false;
            if (connection != null && connection instanceof DatabaseConnection) {
                String cdcTypeMode = ((DatabaseConnection) connection).getCdcTypeMode();
                if (CDCTypeMode.XSTREAM_MODE == CDCTypeMode.indexOf(cdcTypeMode)) {
                    isXstreamCdcTypeMode = true;
                }
            }
            if (isXstreamCdcTypeMode && ((Node) elem).getComponent().getName().equals("tOracleCDC")) {//$NON-NLS-1$
                IMetadataTable table = ((Node) elem).getMetadataList().get(0);
                IElementParameter schemaParam = elem.getElementParameterFromField(EParameterFieldType.SCHEMA_TYPE);
                schemaParam.setValueToDefault(elem.getElementParameters());
                table.setListColumns((((IMetadataTable) schemaParam.getValue()).clone(true)).getListColumns());
            }
            ((Process) ((Node) elem).getProcess()).checkProcess();
            // Added TDQ-11688 show regex when "built-in"
            ITDQPatternService service = null;
            if (GlobalServiceRegister.getDefault().isServiceRegistered(ITDQPatternService.class)) {
                service = GlobalServiceRegister.getDefault().getService(ITDQPatternService.class);
            }
            if (service != null && service.isSinglePatternNode(elem)) {
                IElementParameter regexParameter = ((Node) elem).getElementParameter("PATTERN_REGEX");
                if (regexParameter != null) {
                    regexParameter.setShow(EmfComponent.BUILTIN.equals(this.value));
                }
            }
        }
    }

    private String getParamNameForOldJDBC(IElementParameter param) {
        String paramName = param.getName();
        // for JDBC component of mr process
        if (elem instanceof Node) {
            Node node = (Node) elem;
            if (connection instanceof DatabaseConnection) {
                String databaseType = ((DatabaseConnection) connection).getDatabaseType();
                if ("JDBC".equals(databaseType)) {
                    boolean supportJDBC = false;
                    List<IElementParameter> params = node.getElementParametersFromField(EParameterFieldType.PROPERTY_TYPE);
                    for (IElementParameter proParam : params) {
                        String repositoryValue = proParam.calcRepositoryValue();
                        if (repositoryValue != null && repositoryValue.contains("JDBC")) {
                            supportJDBC = true;
                            break;
                        }
                    }
                    if (supportJDBC) {
                        if (EParameterName.URL.getName().equals(paramName)
                                || EParameterName.URL.getName().equals(param.calcRepositoryValue())) {
                            return "connection.jdbcUrl";
                        } else if (EParameterName.DRIVER_JAR.getName().equals(paramName)) {
                            return "connection.driverTable";
                        } else if (EParameterName.DRIVER_CLASS.getName().equals(paramName)) {
                            return "connection.driverClass";
                        } else if (EParameterName.MAPPING.getName().equals(paramName)) {
                            return "connection.mappingFile";
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private String getParamNameForTacokitDatabaseConnection(IElementParameter param) {
        String paramName = param.getName();
        // for JDBC component of mr process
        if (EParameterName.URL.getName().equals(paramName)
                || EParameterName.URL.getName().equals(param.getRepositoryValue())) {
            return TacokitDatabaseConnection.KEY_URL;
        } else if (EParameterName.DRIVER_JAR.getName().equals(paramName)) {
            return TacokitDatabaseConnection.KEY_DRIVER;
        } else if (EParameterName.DRIVER_CLASS.getName().equals(paramName)) {
            return TacokitDatabaseConnection.KEY_DRIVER_CLASS;
        } else if (EParameterName.MAPPING.getName().equals(paramName)) {
            return TacokitDatabaseConnection.KEY_DATABASE_MAPPING;
        } else if (EParameterName.USER.getName().equals(paramName)
                || JobSettingsConstants.getExtraParameterName(EParameterName.USER.getName()).equals(paramName)) {
            return TacokitDatabaseConnection.KEY_USER_ID;
        } else if (EParameterName.PASS.getName().equals(paramName)
                || JobSettingsConstants.getExtraParameterName(EParameterName.PASS.getName()).equals(paramName)) {
            return TacokitDatabaseConnection.KEY_PASSWORD;
        } 
        
        return null;
    }

    private String getParamNameForImplicitContext(IElementParameter param) {
        String paramName = param.getName();
        if (connection instanceof DatabaseConnection && "JDBC".equals(((DatabaseConnection) connection).getDatabaseType())) {
            String repositoryValue = param.calcRepositoryValue();
            if (EParameterName.URL.getName().equals(paramName)
                    || EParameterName.URL.getName().equals(repositoryValue)) {
                return "connection.jdbcUrl";
            }
            if (EParameterName.DRIVER_JAR.getName().equals(paramName)
                    || EParameterName.DRIVER_JAR.getName().equals(repositoryValue)) {
                return "connection.driverTable";
            }
            if (EParameterName.DRIVER_CLASS.getName().equals(paramName)
                    || EParameterName.DRIVER_CLASS.getName().equals(repositoryValue)) {
                return "connection.driverClass";
            }
            if (EParameterName.MAPPING.getName().equals(paramName)
                    || EParameterName.MAPPING.getName().equals(repositoryValue)) {
                return "connection.mappingFile";
            }
        }
        return null;
    }

    private String getFirstRepositoryTable(Item item) {
        if (isNotSim) {
            if (item != null) {
                // final List<MetadataTable> tables = UpdateRepositoryUtils.getMetadataTablesFromItem(item);
                // if (tables != null && !tables.isEmpty()) {
                // if (tables.size() > index) {
                // for (MetadataTable table : tables) {
                // if (table.getLabel().equals(getCurrentTableName())) {
                // return table.getLabel();
                // }
                // }
                // return tables.get(index).getLabel();
                // } else {
                // return tables.get(0).getLabel();
                // }
                //
                // }
                return getCurrentTableName();
            }
        } else {
            if (item != null) {
                final List<MetadataTable> tables = UpdateRepositoryUtils.getMetadataTablesFromItem(item);
                if (tables != null && !tables.isEmpty()) {
                    return tables.get(0).getLabel();
                }
            }
        }
        return ""; //$NON-NLS-1$
    }

    /**
     * qzhang Comment method "setOtherProperties".
     */
    private void setOtherProperties() {
        boolean metadataInput = false;
        IElementParameter currentParam = elem.getElementParameter(propertyName);

        Item item = null;
        IElementParameter repositoryParam = elem.getElementParameter(EParameterName.REPOSITORY_PROPERTY_TYPE.getName());
        if (repositoryParam != null) {
            item = UpdateRepositoryUtils.getConnectionItemByItemId((String) repositoryParam.getValue());
        }

        if (elem instanceof Node) {
            Node node = (Node) elem;
            if (node.getCurrentActiveLinksNbInput(EConnectionType.FLOW_MAIN) > 0
                    || node.getCurrentActiveLinksNbInput(EConnectionType.FLOW_REF) > 0
                    || node.getCurrentActiveLinksNbInput(EConnectionType.TABLE) > 0
                    || node.getCurrentActiveLinksNbInput(EConnectionType.TABLE_REF) > 0) {
                metadataInput = true;
            }

            boolean hasSchema = elem.getElementParameterFromField(EParameterFieldType.SCHEMA_TYPE) != null ? true
                    : elem.getElementParameterFromField(EParameterFieldType.SCHEMA_REFERENCE) != null;
            if (value.equals(EmfComponent.BUILTIN)) {
                if (!metadataInput && hasSchema) {
                    elem.setPropertyValue(EParameterName.SCHEMA_TYPE.getName(), value);
                }
                elem.setPropertyValue(EParameterName.QUERYSTORE_TYPE.getName(), value);
            } else {
                if (hasSchema) {
                    for (IElementParameter param : new ArrayList<IElementParameter>(elem.getElementParameters())) {
                        if (param.getFieldType().equals(EParameterFieldType.SCHEMA_TYPE)
                                || (param.getFieldType().equals(EParameterFieldType.SCHEMA_REFERENCE)
                                        && EConnectionType.FLOW_MAIN.getName().equals(param.getContext()))) {
                            if (!metadataInput) {
                                IElementParameter repositorySchemaTypeParameter = param.getChildParameters()
                                        .get(EParameterName.REPOSITORY_SCHEMA_TYPE.getName());
                                String repositoryTable = null;
                                if (propertyName.split(":")[1].equals(EParameterName.PROPERTY_TYPE.getName())) { //$NON-NLS-1$
                                    repositoryTable = (String) repositorySchemaTypeParameter.getValue();
                                } else if (item != null) {
                                    if (item instanceof SAPConnectionItem) {
                                        if (getCurrentTableName() != null) {
                                            repositoryTable = item.getProperty().getId() + " - " + getCurrentTableName();
                                        }
                                    } else {
                                        repositoryTable = item.getProperty().getId() + " - " + getFirstRepositoryTable(item); //$NON-NLS-1$
                                    }
                                    repositorySchemaTypeParameter.setValue(repositoryTable);
                                }
                                if (isNotSim) {
                                    if (item != null && UpdateRepositoryUtils.getMetadataTablesFromItem(item) != null) {
                                        if (repositoryTable != null && !"".equals(repositoryTable)) { //$NON-NLS-1$
                                            param.getChildParameters().get(EParameterName.SCHEMA_TYPE.getName())
                                                    .setValue(EmfComponent.REPOSITORY);
                                            IMetadataTable table = MetadataToolHelper.getMetadataFromRepository(repositoryTable);
                                            // repositoryTableMap.get(repositoryTable);
                                            if (table != null) {
                                                table = table.clone();
                                                setDBTableFieldValue(node, table.getTableName(), null);
                                                setSAPFunctionName(node, table.getLabel());
                                                table.setTableName(node.getMetadataList().get(0).getTableName());
                                                if (!table.sameMetadataAs(node.getMetadataList().get(0))) {
                                                    ChangeMetadataCommand cmd = new ChangeMetadataCommand(node, param, null,
                                                            table, param);
                                                    cmd.setConnection(connection);
                                                    cmd.setRepositoryMode(true);
                                                    cmd.execute(true);
                                                }
                                            }
                                        }
                                    }
                                } else if (isMdmOutput()) {
                                    if (item != null && UpdateRepositoryUtils.getMetadataTablesFromItem(item) != null
                                            && UpdateRepositoryUtils.getMetadataTablesFromItem(item).size() > 0) {
                                        if (repositoryTable != null && !"".equals(repositoryTable)) { //$NON-NLS-1$
                                            param.getChildParameters().get(EParameterName.SCHEMA_TYPE.getName())
                                                    .setValue(EmfComponent.REPOSITORY);
                                            IMetadataTable table = MetadataToolHelper.getMetadataFromRepository(repositoryTable);
                                            // repositoryTableMap.get(repositoryTable);
                                            if (table != null) {
                                                table = table.clone();
                                                setDBTableFieldValue(node, table.getTableName(), null);
                                                setSAPFunctionName(node, table.getLabel());
                                                table.setTableName(node.getMetadataList().get(0).getTableName());
                                                ChangeMetadataCommand cmd = new ChangeMetadataCommand(node, param, null, table,
                                                        param);
                                                cmd.setConnection(connection);
                                                cmd.setRepositoryMode(true);
                                                cmd.execute(true);
                                            }
                                        }
                                    }
                                } else {
                                    if (item != null && UpdateRepositoryUtils.getMetadataTablesFromItem(item) != null
                                            && UpdateRepositoryUtils.getMetadataTablesFromItem(item).size() == 1) {
                                        if (repositoryTable != null && !"".equals(repositoryTable)) { //$NON-NLS-1$
                                            param.getChildParameters().get(EParameterName.SCHEMA_TYPE.getName())
                                                    .setValue(EmfComponent.REPOSITORY);
                                            IMetadataTable table = MetadataToolHelper.getMetadataFromRepository(repositoryTable);
                                            // repositoryTableMap.get(repositoryTable);
                                            if (table != null) {
                                                table = table.clone();
                                                setDBTableFieldValue(node, table.getTableName(), null);
                                                setSAPFunctionName(node, table.getLabel());
                                                table.setTableName(node.getMetadataList().get(0).getTableName());
                                                if (!table.sameMetadataAs(node.getMetadataList().get(0))) {
                                                    ChangeMetadataCommand cmd = new ChangeMetadataCommand(node, param, null,
                                                            table, param);
                                                    cmd.setConnection(connection);
                                                    cmd.setRepositoryMode(true);
                                                    cmd.execute(true);
                                                }
                                            }
                                        }
                                    }
                                    // TDI-29176:support tBRMS drag here ,same as tWebService,but for tBRMS's input
                                    // schema,it is invisible which design in the component
                                    else if (item != null && UpdateRepositoryUtils.getMetadataTablesFromItem(item) != null
                                            && (((INode) elem).getComponent().getName().equals("tWebService")
                                                    || ((INode) elem).getComponent().getName().equals("tBRMS"))
                                            && UpdateRepositoryUtils.getMetadataTablesFromItem(item).size() == 2) {
                                        final List<MetadataTable> tables = UpdateRepositoryUtils.getMetadataTablesFromItem(item);
                                        if (tables != null && !tables.isEmpty()) {
                                            if (param.getName().equals("INPUT_SCHEMA")) {
                                                repositoryTable = item.getProperty().getId() + " - " + "Input";
                                                repositorySchemaTypeParameter.setValue(repositoryTable);
                                            } else {
                                                repositoryTable = item.getProperty().getId() + " - " + "Output";
                                                repositorySchemaTypeParameter.setValue(repositoryTable);
                                            }
                                            if (repositoryTable != null && !"".equals(repositoryTable)) { //$NON-NLS-1$
                                                param.getChildParameters().get(EParameterName.SCHEMA_TYPE.getName())
                                                        .setValue(EmfComponent.REPOSITORY);

                                                IMetadataTable table = MetadataToolHelper
                                                        .getMetadataFromRepository(repositoryTable);
                                                if (table != null) {
                                                    table = table.clone();
                                                    setDBTableFieldValue(node, table.getTableName(), null);
                                                    setSAPFunctionName(node, table.getLabel());
                                                    INodeConnector mainConnector = node
                                                            .getConnectorFromType(EConnectionType.FLOW_MAIN);
                                                    IMetadataTable stable = null;
                                                    INodeConnector outputConnector = mainConnector;
                                                    if (mainConnector.getMaxLinkOutput() == 0) {
                                                        for (INodeConnector currentConnector : node.getListConnector()) {
                                                            if (!currentConnector.getBaseSchema()
                                                                    .equals(EConnectionType.FLOW_MAIN.getName())
                                                                    && currentConnector.getMaxLinkOutput() > 0) {
                                                                outputConnector = currentConnector;

                                                            }
                                                        }
                                                    }
                                                    if (param.getName().equals("INPUT_SCHEMA")) {
                                                        stable = node.getMetadataFromConnector("FLOW");

                                                    } else if (param.getName().equals("SCHEMA")) {
                                                        stable = node.getMetadataFromConnector("OUTPUT");
                                                    }
                                                    if (stable != null) {
                                                        table.setTableName(stable.getTableName());
                                                        if (!table.sameMetadataAs(stable)) {
                                                            ChangeMetadataCommand cmd = new ChangeMetadataCommand(node, param,
                                                                    null, table, param);
                                                            cmd.setConnection(connection);
                                                            cmd.setRepositoryMode(true);
                                                            cmd.execute(true);
                                                            // break;
                                                        }
                                                    }

                                                }
                                            }

                                        }

                                    }
                                }

                            } else {
                                Node sourceNode = getRealSourceNode((INode) elem);
                                if (sourceNode != null) {
                                    IMetadataTable sourceMetadataTable = sourceNode.getMetadataList().get(0);
                                    Object sourceSchema = sourceNode.getPropertyValue(EParameterName.SCHEMA_TYPE.getName());
                                    boolean isTake = !sourceNode.isExternalNode() && sourceSchema != null
                                            && elem.getPropertyValue(EParameterName.SCHEMA_TYPE.getName()) != null;
                                    if (isTake && getTake()) {
                                        ChangeMetadataCommand cmd = new ChangeMetadataCommand((Node) elem, param, null,
                                                sourceMetadataTable, param);
                                        cmd.setConnection(connection);
                                        cmd.execute(true);
                                        elem.setPropertyValue(EParameterName.SCHEMA_TYPE.getName(), sourceSchema);
                                        if (sourceSchema.equals(EmfComponent.REPOSITORY)) {
                                            elem.setPropertyValue(EParameterName.REPOSITORY_SCHEMA_TYPE.getName(),
                                                    sourceNode.getPropertyValue(EParameterName.REPOSITORY_SCHEMA_TYPE.getName()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            IElementParameter queryParam = elem.getElementParameterFromField(EParameterFieldType.QUERYSTORE_TYPE,
                    currentParam.getCategory());
            if (queryParam == null) {
                queryParam = elem.getElementParameter(TacokitDatabaseConnection.KEY_DATASET_SQL_QUERY);
            }
            IElementParameter queryStoreType = null;
            if (queryParam != null) {
                queryStoreType = queryParam.getChildParameters().get(EParameterName.QUERYSTORE_TYPE.getName());
            }

            if (item != null) {
                final List<Query> queries = UpdateRepositoryUtils.getQueriesFromItem(item);

                if (propertyName.split(":")[1].equals(EParameterName.PROPERTY_TYPE.getName())) { //$NON-NLS-1$
                    if (queries != null && !queries.isEmpty()) {
                        if (queryParam != null && queryStoreType != null) {
                            queryStoreType.setValue(value);
                            if (value.equals(EmfComponent.REPOSITORY)) {
                                setQueryToRepositoryMode(queryParam, queries, item);
                            }
                        }
                        // query change
                    }
                } else {
                    if (queryParam != null && queryStoreType != null) {
                        if (this.isGuessQuery || queries == null || (queries != null && queries.isEmpty())) {
                            queryStoreType.setValue(EmfComponent.BUILTIN);
                        } else if (queryStoreType != null){
                            queryStoreType.setValue(EmfComponent.REPOSITORY);
                            setQueryToRepositoryMode(queryParam, queries, item);
                        }
                    }
                    List<MetadataTable> tables = UpdateRepositoryUtils.getMetadataTablesFromItem(item);
                    if (tables == null || tables.isEmpty()) {
                        elem.setPropertyValue(EParameterName.SCHEMA_TYPE.getName(), EmfComponent.BUILTIN);
                    } else {
                        if (table != null && table.getTableName() != null) {
                            setDBTableFieldValue(node, table.getTableName(), null);
                        }
                    }
                }
            }
            if (table != null && item != null) {
                IElementParameter cdsParam = elem.getElementParameter("CDS_VIEWS");
                IElementParameter listParam = elem.getElementParameter("CDS_VIEW_PARAMETERS");
                if (cdsParam != null && listParam != null && SAPConnectionItem.class.isInstance(item)) {
                    Set<MetadataTable> tables = ConnectionHelper.getTables(SAPConnectionItem.class.cast(item).getConnection());
                    MetadataTable metaTable = tables.stream().filter(Objects::nonNull)
                            .filter(t -> t.getId().equals(table.getId()))
                            .filter(t -> ERepositoryObjectType.METADATA_SAP_CDS_VIEW.name()
                                    .equals(TaggedValueHelper.getValueString(EProperties.CONTENT_TYPE.name(), t)))
                            .findFirst().orElse(null);
                    if (metaTable != null) {
                        List<Map<String, Object>> list = (List<Map<String, Object>>) listParam.getValue();
                        list.clear();
                        metaTable.getOwnedElement().stream().filter(MetadataColumn.class::isInstance)
                                .map(MetadataColumn.class::cast).map(MetadataColumn::getName).forEach(name -> {
                                    Map<String, Object> map = new HashMap<>();
                                    map.put("PARAMETER", TalendQuoteUtils.addQuotesIfNotExist(name));
                                    map.put("VALUE", "\"\"");
                                    list.add(map);
                                });
                        cdsParam.setValue(true);
                    }
                }
            }
        }
    }

    private boolean isMdmOutput() {
        if (elem instanceof Node) {
            Node node = (Node) elem;
            if ("tMDMOutput".equals(node.getComponent().getName())) {
                return true;
            }
        }
        return false;
    }

    private void setQueryToRepositoryMode(IElementParameter queryParam, List<Query> queries, Item item) {

        IElementParameter repositoryParam = queryParam.getChildParameters()
                .get(EParameterName.REPOSITORY_QUERYSTORE_TYPE.getName());
        Query query = UpdateRepositoryUtils.getQueryById(item, (String) repositoryParam.getValue());
        if (query == null && queries != null && !queries.isEmpty()) {
            query = queries.get(0);
            if (query != null) {
                repositoryParam.setValue(item.getProperty().getId() + " - " + query.getLabel()); //$NON-NLS-1$
            }

        }

        if (query != null) {
            IElementParameter memoSqlParam = elem.getElementParameterFromField(EParameterFieldType.MEMO_SQL,
                    queryParam.getCategory());
            String queryStr = QueryUtil.checkAndAddQuotes(query.getValue());
            memoSqlParam.setValue(queryStr);
            memoSqlParam.setRepositoryValueUsed(true);
        }
    }

    @SuppressWarnings("unchecked")
    protected Node getRealSourceNode(INode target) {
        Node sourceNode = null;
        IODataComponent input = null;
        List<org.talend.designer.core.ui.editor.connections.Connection> incomingConnections = null;
        incomingConnections = (List<org.talend.designer.core.ui.editor.connections.Connection>) target.getIncomingConnections();
        for (org.talend.designer.core.ui.editor.connections.Connection connec : incomingConnections) {
            if (connec.isActivate() && connec.getLineStyle().hasConnectionCategory(IConnectionCategory.DATA)) {
                input = new IODataComponent(connec);
            }
        }
        if (input != null) {
            INode source = input.getSource();
            if (source instanceof Node) {
                sourceNode = (Node) source;
                // final IExternalNode externalNode =
                // sourceNode.getExternalNode();
                // if (sourceNode.getPluginFullName() != null &&
                // !"".equals(sourceNode.getPluginFullName())
                // && sourceNode.getExternalNode() != null) {
                // return getRealSourceNode(externalNode);
                // }
            }
        }
        return sourceNode;
    }

    /**
     * qzhang Comment method "getTake".
     *
     * @return
     */
    private Boolean take = null;

    private boolean getTake() {
        if (take == null) {
            take = MessageDialog.openQuestion(DisplayUtils.getDefaultShell(false), "", Messages //$NON-NLS-1$
                    .getString("ChangeValuesFromRepository.messageDialog.takeMessage")); //$NON-NLS-1$
        }
        return take;
    }

    @Override
    public void undo() {
        // Force redraw of Commponents propoerties
        elem.setPropertyValue(updataComponentParamName, new Boolean(true));

        if (propertyName.split(":")[1].equals(propertyTypeName) && (EmfComponent.BUILTIN.equals(value))) { //$NON-NLS-1$
            for (IElementParameter param : elem.getElementParameters()) {
                String repositoryValue = param.calcRepositoryValue();
                if (param.isShow(elem.getElementParameters()) && (repositoryValue != null)
                        && (!param.getName().equals(propertyTypeName))) {
                    boolean paramFlag = JobSettingsConstants.isExtraParameter(param.getName());
                    boolean extraFlag = JobSettingsConstants.isExtraParameter(propertyTypeName);
                    if (paramFlag == extraFlag) {
                        // for job settings extra.(feature 2710)
                        param.setRepositoryValueUsed(true);
                    }
                }
            }
        } else {
            for (IElementParameter param : elem.getElementParameters()) {
                String repositoryValue = param.calcRepositoryValue();
                if (param.isShow(elem.getElementParameters()) && (repositoryValue != null)) {
                    Object objectValue = RepositoryToComponentProperty.getValue(connection, repositoryValue, null);
                    if (dragAndDropAction == true && connection instanceof XmlFileConnectionImpl) {
                        objectValue = RepositoryToComponentProperty.getXmlAndXSDFileValue((XmlFileConnection) connection,
                                repositoryValue);
                        dragAndDropAction = false;
                    }
                    if (objectValue != null) {
                        elem.setPropertyValue(param.getName(), oldValues.get(param.getName()));
                        param.setRepositoryValueUsed(false);
                    }
                }
            }
        }
        IElementParameter currentParam = elem.getElementParameter(propertyName);
        if (propertyName.split(":")[1].equals(propertyTypeName)) { //$NON-NLS-1$
            if (value.equals(EmfComponent.BUILTIN)) {
                currentParam.setValue(EmfComponent.REPOSITORY);
            } else {
                currentParam.setValue(EmfComponent.BUILTIN);
                IElementParameter schemaParam = elem.getElementParameterFromField(EParameterFieldType.SCHEMA_TYPE,
                        currentParam.getCategory());
                if (schemaParam == null) {
                    schemaParam = elem.getElementParameterFromField(EParameterFieldType.SCHEMA_REFERENCE,
                            currentParam.getCategory());
                }
                if (schemaParam != null) {
                    IElementParameter schemaType = schemaParam.getChildParameters().get(EParameterName.SCHEMA_TYPE.getName());
                    schemaType.setValue(EmfComponent.BUILTIN);
                }

                IElementParameter queryParam = elem.getElementParameterFromField(EParameterFieldType.QUERYSTORE_TYPE,
                        currentParam.getCategory());
                if (queryParam != null) {
                    IElementParameter queryStoreType = queryParam.getChildParameters()
                            .get(EParameterName.QUERYSTORE_TYPE.getName());
                    queryStoreType.setValue(EmfComponent.BUILTIN);
                }
            }
        } else {
            elem.setPropertyValue(propertyName, oldMetadata);
        }

        JobSettings.switchToCurJobSettingsView();
    }

    /**
     * Sets a sets of maps.
     *
     * @param tablesmap
     * @param queriesmap
     * @param repositoryTableMap
     */

    public void setMaps(Map<String, IMetadataTable> repositoryTableMap) {
    }

    /**
     *
     * ggu Comment method "isGuessQuery".
     *
     * for guess query
     *
     * @return
     */
    public boolean isGuessQuery() {
        return this.isGuessQuery;
    }

    public void setGuessQuery(boolean isGuessQuery) {
        this.isGuessQuery = isGuessQuery;
    }

    /**
     * Getter for sapFunctionName.
     *
     * @return the sapFunctionName
     */
    public String getSapFunctionLabel() {
        // // Use the first function
        // if (this.sapFunctionLabel == null) {
        //
        // if (connection == null) {
        // return null;
        // }
        //
        // if (!(connection instanceof SAPConnection)) {
        // return null;
        // }
        // SAPConnection sapConn = (SAPConnection) connection;
        // if (sapConn.getFuntions() != null && !sapConn.getFuntions().isEmpty()) {
        // return sapConn.getFuntions().get(0).getLabel();
        // }
        // }
        return this.sapFunctionLabel;
    }

    /**
     * Sets the sapFunctionName.
     *
     * @param sapFunctionName the sapFunctionName to set
     */
    public void setSapFunctionLabel(String sapFunctionName) {
        this.sapFunctionLabel = sapFunctionName;
    }

    public String getSapIDocLabel() {
        // Use the first function
        if (this.sapIDocLabel == null) {

            if (connection == null) {
                return null;
            }

            if (!(connection instanceof SAPConnection)) {
                return null;
            }
            SAPConnection sapConn = (SAPConnection) connection;
            if (sapConn.getIDocs() != null && !sapConn.getIDocs().isEmpty()) {
                return sapConn.getIDocs().get(0).getName();
            }
        }
        return this.sapIDocLabel;
    }

    public void setSapIDocLabel(String sapIDocLabel) {
        this.sapIDocLabel = sapIDocLabel;
    }

    public boolean isIgnoreContextMode() {
        return this.ignoreContextMode;
    }

    public void ignoreContextMode(boolean ignore) {
        this.ignoreContextMode = ignore;
    }

    public String getCurrentTableName() {
        return this.currentTableName;
    }

    public void setCurrentTableName(String currentTableName) {
        this.currentTableName = currentTableName;
    }

    public void setSalesForceModuleUnit(SalesforceModuleUnit moduleUnit) {
        this.moduleUnit = moduleUnit;
    }

    public void setTable(IMetadataTable table) {
        this.table = table;
    }
}

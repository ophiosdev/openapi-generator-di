/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import com.samskivert.mustache.Mustache;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import lombok.Setter;
import org.openapitools.codegen.*;
import org.openapitools.codegen.meta.features.*;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.URLPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.*;

import static java.util.UUID.randomUUID;

public class AspNetDependencyInjectGenerator extends AbstractCSharpCodegen {
    private final Logger LOGGER = LoggerFactory.getLogger(AspNetDependencyInjectGenerator.class);

    private static final String NAME                 = "aspnetcore-di";

    public static final String MODEL_POCOMODE            = "pocoModels";
    public static final String USE_MODEL_SEPERATEPROJECT = "useSeparateModelProject";
    public static final String ASPNET_CORE_VERSION       = "aspnetCoreVersion";
    public static final String OPERATION_IS_ASYNC        = "operationIsAsync";
    public static final String OPERATION_RESULT_TASK     = "operationResultTask";
    public static final String GENERATE_BODY             = "generateBody";
    public static final String TARGET_FRAMEWORK          = "targetFramework";
    public static final String MODEL_CLASS_MODIFIER      = "modelClassModifier";

    public static final String PROJECT_SDK = "projectSdk";
    public static final String SDK_LIB     = "Microsoft.NET.Sdk";

    @Setter private String packageGuid = "{" + randomUUID().toString().toUpperCase(Locale.ROOT) + "}";
    private String userSecretsGuid     = randomUUID().toString();

    private boolean pocoModels              = false;
    private boolean useSeparateModelProject = false;
    private String projectSdk               = SDK_LIB;
    private String compatibilityVersion     = "Version_2_2";
    private boolean operationIsAsync        = true;
    private boolean operationResultTask     = true;

    protected CliOption aspnetCoreVersion = new CliOption(ASPNET_CORE_VERSION, "ASP.NET Core version: 9.0, 8.0, 7.0, 6.0 (deprecated)");
    private CliOption modelClassModifier = new CliOption(MODEL_CLASS_MODIFIER, "Model Class Modifier can be nothing or partial");

    public AspNetDependencyInjectGenerator() {
        super();

        // TODO: AspnetCore community review
        modifyFeatureSet(features -> features
                .includeDocumentationFeatures(DocumentationFeature.Readme)
                .excludeWireFormatFeatures(WireFormatFeature.PROTOBUF)
                .securityFeatures(EnumSet.of(
                        SecurityFeature.ApiKey,
                        SecurityFeature.BasicAuth,
                        SecurityFeature.BearerToken
                ))
                .excludeGlobalFeatures(
                        GlobalFeature.XMLStructureDefinitions,
                        GlobalFeature.Callbacks,
                        GlobalFeature.LinkObjects,
                        GlobalFeature.ParameterStyling,
                        GlobalFeature.MultiServer
                )
                .includeSchemaSupportFeatures(
                        SchemaSupportFeature.Polymorphism
                )
                .includeParameterFeatures(
                        ParameterFeature.Cookie
                )
        );

        modelTemplateFiles.put("model.mustache", ".cs");
        apiTemplateFiles.put("controller.mustache", ".cs");

        embeddedTemplateDir = templateDir = getName();

        // contextually reserved words
        // NOTE: C# uses camel cased reserved words, while models are title cased. We don't want lowercase comparisons.
        reservedWords.addAll(
                Arrays.asList("var", "async", "await", "dynamic", "yield")
        );

        cliOptions.clear();

        setSupportNullable(Boolean.TRUE);

        // CLI options
        addOption(CodegenConstants.PACKAGE_DESCRIPTION,
                CodegenConstants.PACKAGE_DESCRIPTION_DESC,
                packageDescription);

        addOption(CodegenConstants.LICENSE_URL,
                CodegenConstants.LICENSE_URL_DESC,
                licenseUrl);

        addOption(CodegenConstants.LICENSE_NAME,
                CodegenConstants.LICENSE_NAME_DESC,
                licenseName);

        addOption(CodegenConstants.PACKAGE_COPYRIGHT,
                CodegenConstants.PACKAGE_COPYRIGHT_DESC,
                packageCopyright);

        addOption(CodegenConstants.PACKAGE_AUTHORS,
                CodegenConstants.PACKAGE_AUTHORS_DESC,
                packageAuthors);

        addOption(CodegenConstants.PACKAGE_TITLE,
                CodegenConstants.PACKAGE_TITLE_DESC,
                packageTitle);

        addOption(CodegenConstants.PACKAGE_NAME,
                "C# package name (convention: Title.Case).",
                packageName);

        addOption(CodegenConstants.PACKAGE_VERSION,
                "C# package version.",
                packageVersion);

        addOption(CodegenConstants.OPTIONAL_PROJECT_GUID,
                CodegenConstants.OPTIONAL_PROJECT_GUID_DESC,
                null);

        aspnetCoreVersion.addEnum("6.0", "ASP.NET Core 6.0");
        aspnetCoreVersion.addEnum("7.0", "ASP.NET Core 7.0");
        aspnetCoreVersion.addEnum("8.0", "ASP.NET Core 8.0");
        aspnetCoreVersion.addEnum("9.0", "ASP.NET Core 9.0");
        aspnetCoreVersion.setDefault("8.0");
        aspnetCoreVersion.setOptValue(aspnetCoreVersion.getDefault());
        cliOptions.add(aspnetCoreVersion);

        // CLI Switches
        addSwitch(CodegenConstants.NULLABLE_REFERENCE_TYPES,
                CodegenConstants.NULLABLE_REFERENCE_TYPES_DESC,
                this.nullReferenceTypesFlag);

        addSwitch(CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG,
                CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG_DESC,
                sortParamsByRequiredFlag);

        addSwitch(CodegenConstants.USE_DATETIME_OFFSET,
                CodegenConstants.USE_DATETIME_OFFSET_DESC,
                useDateTimeOffsetFlag);

        addSwitch(CodegenConstants.USE_DATETIME_FOR_DATE,
                CodegenConstants.USE_DATETIME_FOR_DATE_DESC,
                useDateTimeForDateFlag);

        addSwitch(CodegenConstants.USE_COLLECTION,
                CodegenConstants.USE_COLLECTION_DESC,
                useCollection);

        addSwitch(CodegenConstants.RETURN_ICOLLECTION,
                CodegenConstants.RETURN_ICOLLECTION_DESC,
                returnICollection);

        addSwitch(MODEL_POCOMODE,
                "Build POCO Models",
                pocoModels);

        addSwitch(USE_MODEL_SEPERATEPROJECT,
                "Create a separate project for models",
                useSeparateModelProject);

        addOption(CodegenConstants.ENUM_NAME_SUFFIX,
                CodegenConstants.ENUM_NAME_SUFFIX_DESC,
                enumNameSuffix);

        addOption(CodegenConstants.ENUM_VALUE_SUFFIX,
                "Suffix that will be appended to all enum values.",
                enumValueSuffix);

        addSwitch(OPERATION_IS_ASYNC,
                "Set methods to async (default) or sync.",
                operationIsAsync);

        addSwitch(OPERATION_RESULT_TASK,
                "Set methods result to Task<> (default).",
                operationResultTask);

        modelClassModifier.setType("String");
        modelClassModifier.addEnum("", "Keep model class default with no modifier");
        modelClassModifier.addEnum("partial", "Make model class partial");
        modelClassModifier.setDefault("partial");
        modelClassModifier.setOptValue(modelClassModifier.getDefault());
        addOption(modelClassModifier.getOpt(), modelClassModifier.getDescription(), modelClassModifier.getOptValue());
    }

    @Deprecated
    @Override
    protected Set<String> getNullableTypes() {
        return new HashSet<>(
            Arrays.asList(
                "decimal",
                "bool",
                "int",
                "uint",
                "long",
                "ulong",
                "float",
                "double",
                "DateTime",
                "DateOnly",
                "DateTimeOffset",
                "Guid"
            )
        );
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        super.postProcessParameter(parameter);

        if (!parameter.dataType.endsWith("?") && !parameter.required && (nullReferenceTypesFlag || getNullableTypes().contains(parameter.dataType))) {
            parameter.dataType = parameter.dataType + "?";
        }
    }

    @Override
    protected void updateCodegenParameterEnum(CodegenParameter parameter, CodegenModel model) {
        super.updateCodegenParameterEnumLegacy(parameter, model);

        if (!parameter.required && parameter.vendorExtensions.get("x-csharp-value-type") != null) { //optional
            parameter.dataType = parameter.dataType + "?";
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getHelp() {
        return "Generate ASP.NET Core Web API stubs.";
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);
        URL url = URLPathUtils.getServerURL(openAPI, serverVariableOverrides());
        additionalProperties.put("serverHost", url.getHost());
        additionalProperties.put("serverPort", URLPathUtils.getPort(url, 8080));
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(CodegenConstants.OPTIONAL_PROJECT_GUID)) {
            setPackageGuid((String) additionalProperties.get(CodegenConstants.OPTIONAL_PROJECT_GUID));
        }
        additionalProperties.put("packageGuid", packageGuid);

        if (!additionalProperties.containsKey("packageGuid")) {
            additionalProperties.put("packageGuid", packageGuid);
        } else {
            packageGuid = (String) additionalProperties.get("packageGuid");
        }

        if (!additionalProperties.containsKey("userSecretsGuid")) {
            additionalProperties.put("userSecretsGuid", userSecretsGuid);
        } else {
            userSecretsGuid = (String) additionalProperties.get("userSecretsGuid");
        }

        // Check for the modifiers etc.
        // The order of the checks is important.
        setModelClassModifier();
        setPocoModels();
        setOperationIsAsync();

        // Check for class modifier if not present set the default value.
        additionalProperties.put(PROJECT_SDK, projectSdk);

        if (!additionalProperties.containsKey(CodegenConstants.API_PACKAGE)) {
            apiPackage = packageName + ".Controllers";
            additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);
        }

        if (!additionalProperties.containsKey(CodegenConstants.MODEL_PACKAGE)) {
            modelPackage = packageName + ".Models";
            additionalProperties.put(CodegenConstants.MODEL_PACKAGE, modelPackage);
        }

        setFramework();

        supportingFiles.add(new SupportingFile("validateModel.mustache", "Attributes", "ValidateModelStateAttribute.cs"));
        supportingFiles.add(new SupportingFile("typeExtensions.mustache", "Extensions", "TypeExtensions.cs"));

        supportingFiles.add(new SupportingFile("typeConverter.mustache", "Converters", "CustomEnumConverter.cs"));

        supportingFiles.add(new SupportingFile("apiAuthentication.mustache", "Authentication", "ApiAuthentication.cs"));
        supportingFiles.add(new SupportingFile("inputFormatterStream.mustache", "Formatters", "InputFormatterStream.cs"));

        setTypeMapping();
    }

    @Override
    public String apiFileFolder() {
        return outputFolder;
    }

    @Override
    public String modelFileFolder() {
        return apiFileFolder() + File.separator + "Models";
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        generateJSONSpecFile(objs);
        return super.postProcessSupportingFileData(objs);
    }

    @Override
    protected void processOperation(CodegenOperation operation) {
        super.processOperation(operation);

        // HACK: Unlikely in the wild, but we need to clean operation paths for MVC Routing
        if (operation.path != null) {
            String original = operation.path;
            operation.path = operation.path.replace("?", "/");
            if (!original.equals(operation.path)) {
                LOGGER.warn("Normalized {} to {}. Please verify generated source.", original, operation.path);
            }
        }

        // Converts, for example, PUT to HttpPut for controller attributes
        operation.httpMethod = "Http" + operation.httpMethod.charAt(0) + operation.httpMethod.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        super.postProcessOperationsWithModels(objs, allModels);
        // We need to postprocess the operations to add proper consumes tags and fix form file handling
        if (objs != null) {
            OperationMap operations = objs.getOperations();
            if (operations != null) {
                List<CodegenOperation> ops = operations.getOperation();
                for (CodegenOperation operation : ops) {
                    if (operation.consumes == null) {
                        continue;
                    }
                    if (operation.consumes.size() == 0) {
                        continue;
                    }

                    // Build a consumes string for the operation we cannot iterate in the template as we need a ','
                    // after each entry but the last

                    StringBuilder consumesString = new StringBuilder();
                    for (Map<String, String> consume : operation.consumes) {
                        if (!consume.containsKey("mediaType")) {
                            continue;
                        }

                        if (consumesString.toString().isEmpty()) {
                            consumesString = new StringBuilder("\"" + consume.get("mediaType") + "\"");
                        } else {
                            consumesString.append(", \"").append(consume.get("mediaType")).append("\"");
                        }

                        // In a multipart/form-data consuming context binary data is best handled by an IFormFile
                        if (!consume.get("mediaType").equals("multipart/form-data")) {
                            continue;
                        }

                        // Change dataType of binary parameters to IFormFile for formParams in multipart/form-data
                        for (CodegenParameter param : operation.formParams) {
                            if (param.isBinary) {
                                param.dataType = "IFormFile";
                                param.baseType = "IFormFile";
                            }
                        }

                        for (CodegenParameter param : operation.allParams) {
                            if (param.isBinary && param.isFormParam) {
                                param.dataType = "IFormFile";
                                param.baseType = "IFormFile";
                            }
                        }
                    }

                    if (!consumesString.toString().isEmpty()) {
                        operation.vendorExtensions.put("x-aspnetcore-consumes", consumesString.toString());
                    }
                }
            }
        }
        return objs;
    }

    @Override
    public Mustache.Compiler processCompiler(Mustache.Compiler compiler) {
        // To avoid unexpected behaviors when options are passed programmatically such as { "useCollection": "" }
        return super.processCompiler(compiler).emptyStringIsFalse(true);
    }

    @Override
    public String toRegularExpression(String pattern) {
        return escapeText(pattern);
    }

    @Override
    protected void patchProperty(Map<String, CodegenModel> enumRefs, CodegenModel model, CodegenProperty property) {
        super.patchProperty(enumRefs, model, property);

        if (!property.isContainer && (getNullableTypes().contains(property.dataType) || property.isEnum)) {
            property.vendorExtensions.put("x-csharp-value-type", true);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public String getNullableType(Schema p, String type) {
        if (languageSpecificPrimitives.contains(type)) {
            if (isSupportNullable() && ModelUtils.isNullable(p) && (getNullableTypes().contains(type) || nullReferenceTypesFlag)) {
                return type + "?";
            } else {
                return type;
            }
        } else {
            return null;
        }
    }

    @Override
    protected void patchVendorExtensionNullableValueType(CodegenParameter parameter) {
        super.patchVendorExtensionNullableValueTypeLegacy(parameter);
    }

    private void setCliOption(CliOption cliOption) throws IllegalArgumentException {
        if (additionalProperties.containsKey(cliOption.getOpt())) {
            // TODO Hack - not sure why the empty strings become boolean.
            Object obj = additionalProperties.get(cliOption.getOpt());
            if (!SchemaTypeUtil.BOOLEAN_TYPE.equals(cliOption.getType())) {
                if (obj instanceof Boolean) {
                    obj = "";
                    additionalProperties.put(cliOption.getOpt(), obj);
                }
            }
            cliOption.setOptValue(obj.toString());
        } else {
            additionalProperties.put(cliOption.getOpt(), cliOption.getOptValue());
        }
        if (cliOption.getOptValue() == null) {
            cliOption.setOptValue(cliOption.getDefault());
            throw new IllegalArgumentException(cliOption.getOpt() + ": Invalid value '" + additionalProperties.get(cliOption.getOpt()).toString() + "'" +
                    ". " + cliOption.getDescription());
        }
    }

    private void setAspnetCoreVersion(String packageFolder) {
        setCliOption(aspnetCoreVersion);
    }

    private void setPocoModels() {
        if (additionalProperties.containsKey(MODEL_POCOMODE)) {
            pocoModels = convertPropertyToBooleanAndWriteBack(MODEL_POCOMODE);
        } else {
            additionalProperties.put(MODEL_POCOMODE, pocoModels);
        }
    }

    private void setUseSeparateModelProject() {
        if (additionalProperties.containsKey(USE_MODEL_SEPERATEPROJECT)) {
            useSeparateModelProject = convertPropertyToBooleanAndWriteBack(USE_MODEL_SEPERATEPROJECT);
            if (useSeparateModelProject) {
                LOGGER.info("Using separate model project");
            }
        } else {
            additionalProperties.put(USE_MODEL_SEPERATEPROJECT, useSeparateModelProject);
        }
    }

    private void setOperationIsAsync() {
        if (additionalProperties.containsKey(OPERATION_IS_ASYNC)) {
            operationIsAsync = convertPropertyToBooleanAndWriteBack(OPERATION_IS_ASYNC);
        } else {
            additionalProperties.put(OPERATION_IS_ASYNC, operationIsAsync);
        }
    }

    private void setFramework() throws IllegalArgumentException {
        if (aspnetCoreVersion.getOptValue().startsWith("6.")) {
            LOGGER.warn(
                    "ASP.NET core version is {} so changing to use frameworkReference instead of packageReference ",
                    aspnetCoreVersion.getOptValue());
            additionalProperties.put(TARGET_FRAMEWORK, "net6.0");
        } else if (aspnetCoreVersion.getOptValue().startsWith("7.")) {
            LOGGER.warn(
                    "ASP.NET core version is {} so changing to use frameworkReference instead of packageReference ",
                    aspnetCoreVersion.getOptValue());
            additionalProperties.put(TARGET_FRAMEWORK, "net7.0");
        } else if (aspnetCoreVersion.getOptValue().startsWith("8.")) {
            LOGGER.warn(
                    "ASP.NET core version is {} so changing to use frameworkReference instead of packageReference ",
                    aspnetCoreVersion.getOptValue());
            additionalProperties.put(TARGET_FRAMEWORK, "net8.0");
        } else if (aspnetCoreVersion.getOptValue().startsWith("9.")) {
            LOGGER.warn(
                    "ASP.NET core version is {} so changing to use frameworkReference instead of packageReference ",
                    aspnetCoreVersion.getOptValue());
            additionalProperties.put(TARGET_FRAMEWORK, "net9.0");
        } else {
            throw new IllegalArgumentException("Unsupported ASP.NET core version: " + aspnetCoreVersion.getOptValue());
        }
    }

    private void setModelClassModifier() {
        setCliOption(modelClassModifier);
    }
}

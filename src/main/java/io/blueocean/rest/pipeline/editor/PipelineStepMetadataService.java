package io.blueocean.rest.pipeline.editor;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.verb.GET;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import hudson.Extension;
import hudson.ExtensionList;
import io.jenkins.blueocean.commons.stapler.TreeResponse;
import io.jenkins.blueocean.rest.ApiRoutable;
import jenkins.model.Jenkins;

/**
 * This provides and Blueocean REST API endpoint to obtain pipeline step metadata.
 */
@Extension
public class PipelineStepMetadataService implements ApiRoutable {
    ParameterNameDiscoverer nameFinder = new LocalVariableTableParameterNameDiscoverer();

    @Override
    public String getUrlName() {
        return "pipeline-step-metadata";
    }
    
    /**
     * Basic exported model for {@link PipelineStepMetadata}
     */
    @ExportedBean
    public static class BasicPipelineStepMetadata implements PipelineStepMetadata {
        private String displayName;
        private String functionName;
        private Class<?> type;
        private String descriptorUrl;
        private List<Class<?>> requiredContext = new ArrayList<Class<?>>();
        private List<Class<?>> providedContext = new ArrayList<Class<?>>();
        private boolean isWrapper = false;
        private String snippetizerUrl;
        private List<PipelineStepPropertyMetadata> props = new ArrayList<PipelineStepPropertyMetadata>();
        
        public BasicPipelineStepMetadata(String functionName, Class<?> type, String displayName) {
            super();
            this.displayName = displayName;
            this.type = type;
            this.functionName = functionName;
        }

        @Exported
        @Override
        public String getDisplayName() {
            return displayName;
        }
        
        @Exported
        @Override
        public String getFunctionName() {
            return functionName;
        }
        
        @Exported
        @Override
        public String[] getRequiredContext() {
            List<String> out = new ArrayList<String>();
            for (Class<?> c : requiredContext) {
                out.add(c.getName());
            }
            return out.toArray(new String[out.size()]);
        }
        
        @Exported
        @Override
        public String[] getProvidedContext() {
            List<String> out = new ArrayList<String>();
            for (Class<?> c : providedContext) {
                out.add(c.getName());
            }
            return out.toArray(new String[out.size()]);
        }
        
        @Exported
        @Override
        public String getSnippetizerUrl() {
            return snippetizerUrl;
        }
        
        @Exported
        public String descriptorUrl() {
            return descriptorUrl;
        }
        
        @Exported
        @Override
        public boolean getIsBlockContainer() {
            return isWrapper;
        }

        @Exported
        @Override
        public String getType() {
            return type.getName();
        }

        @Exported
        @Override
        public PipelineStepPropertyMetadata[] getProperties() {
            return props.toArray(new PipelineStepPropertyMetadata[props.size()]);
        }
    }
    
    /**
     * Basic exported model for {@link PipelineStepPropertyDescriptor)
     */
    @ExportedBean
    public static class BasicPipelineStepPropertyMetadata implements PipelineStepPropertyMetadata{
        private String name;
        private Class<?> type;
        private boolean isRequired = false;

        @Exported
        @Override
        public String getName() {
            return name;
        }

        @Exported
        @Override
        public String getType() {
            return type.getName();
        }

        @Exported
        @Override
        public boolean getIsRequired() {
            return isRequired;
        }
    }
    
    /**
     * Function to return all step descriptors present in the system when accessed through the REST API
     */
    @GET
    @WebMethod(name = "")
    @TreeResponse
    public PipelineStepMetadata[] getPipelineStepMetadata() throws IOException {
        Jenkins j = Jenkins.getInstance();
        Snippetizer snippetizer = ExtensionList.create(j, Snippetizer.class).get(0);

        List<PipelineStepMetadata> pd = new ArrayList<PipelineStepMetadata>();
        // POST to this with parameter names
        // e.g. json:{"time": "1", "unit": "NANOSECONDS", "stapler-class": "org.jenkinsci.plugins.workflow.steps.TimeoutStep", "$class": "org.jenkinsci.plugins.workflow.steps.TimeoutStep"}
        String snippetizerUrl = Stapler.getCurrentRequest().getContextPath() + "/" + snippetizer.getUrlName() + "/generateSnippet";

        for (StepDescriptor d : StepDescriptor.all()) {
            PipelineStepMetadata step = descriptorMetadata(d, snippetizerUrl);
            pd.add(step);
        }

        return pd.toArray(new PipelineStepMetadata[pd.size()]);
    }

    private PipelineStepMetadata descriptorMetadata(StepDescriptor d, String snippetizerUrl) {
        BasicPipelineStepMetadata meta = new BasicPipelineStepMetadata(d.getFunctionName(), d.clazz, d.getDisplayName());
        meta.snippetizerUrl = snippetizerUrl + "?$class=" + d.clazz.getName();
        
        meta.isWrapper = d.takesImplicitBlockArgument();
        meta.requiredContext.addAll(d.getRequiredContext());
        meta.providedContext.addAll(d.getProvidedContext());
        meta.descriptorUrl = d.getDescriptorUrl();

        for (Method m : d.clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(DataBoundSetter.class)) {
                String paramName = StringUtils.uncapitalize(m.getName().substring(3));
                Class<?> paramType = m.getParameterTypes()[0];
                BasicPipelineStepPropertyMetadata param = new BasicPipelineStepPropertyMetadata();
                param.name = paramName;
                param.type = paramType;
                meta.props.add(param);
            }
        }

        for (Constructor<?> c : d.clazz.getDeclaredConstructors()) {
            if (c.isAnnotationPresent(DataBoundConstructor.class)) {
                Class<?>[] paramTypes = c.getParameterTypes();
                String[] paramNames = nameFinder.getParameterNames(c);
                if(paramNames != null) {
                    for (int i = 0; i < paramNames.length; i++) {
                        String paramName = paramNames[i];
                        Class<?> paramType = paramTypes[i];
                        BasicPipelineStepPropertyMetadata param = new BasicPipelineStepPropertyMetadata();
                        param.name = paramName;
                        param.type = paramType;
                        meta.props.add(param);
                    }
                }
            }
        }

        return meta;
    }
}

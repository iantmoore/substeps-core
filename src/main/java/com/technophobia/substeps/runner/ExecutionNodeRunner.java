/*
 *	Copyright Technophobia Ltd 2012
 *
 *   This file is part of Substeps.
 *
 *    Substeps is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    Substeps is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with Substeps.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.technophobia.substeps.runner;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.technophobia.substeps.execution.DryRunImplementationCache;
import com.technophobia.substeps.execution.ImplementationCache;
import com.technophobia.substeps.execution.MethodExecutor;
import com.technophobia.substeps.execution.node.RootNodeExecutionContext;
import com.technophobia.substeps.execution.node.RootNode;
import com.technophobia.substeps.model.Scope;
import com.technophobia.substeps.model.Syntax;
import com.technophobia.substeps.runner.builder.ExecutionNodeTreeBuilder;
import com.technophobia.substeps.runner.node.RootNodeRunner;
import com.technophobia.substeps.runner.setupteardown.SetupAndTearDown;
import com.technophobia.substeps.runner.syntax.SyntaxBuilder;

/**
 * Takes a tree of execution nodes and executes them, all variables, args,
 * backgrounds already pre-determined
 * 
 * @author ian
 * 
 */
public class ExecutionNodeRunner implements SubstepsRunner {

    private static final String DRY_RUN_KEY = "dryRun";

    private static final Logger log = LoggerFactory.getLogger(ExecutionNodeRunner.class);

    private RootNode rootNode;

    private final INotificationDistributor notificationDistributor = new NotificationDistributor();

    private RootNodeExecutionContext nodeExecutionContext;

    private final MethodExecutor methodExecutor = new ImplementationCache();

    private final RootNodeRunner rootNodeRunner = new RootNodeRunner();

    private List<SubstepExecutionFailure> failures;

    public void addNotifier(final INotifier notifier) {

        notificationDistributor.addListener(notifier);
    }

    public RootNode prepareExecutionConfig(final SubstepsExecutionConfig theConfig) {

        ExecutionConfigWrapper config = new ExecutionConfigWrapper(theConfig);
        config.initProperties();

        SetupAndTearDown setupAndTearDown = new SetupAndTearDown(config.getInitialisationClasses(), methodExecutor);

        final String loggingConfigName = config.getDescription() != null ? config.getDescription() : "SubStepsMojo";

        setupAndTearDown.setLoggingConfigName(loggingConfigName);

        final TagManager tagmanager = new TagManager(config.getTags());

        final TagManager nonFatalTagmanager = (config.getNonFatalTags() != null) ? new TagManager(
                config.getNonFatalTags()) : null;

        File subStepsFile = null;

        if (config.getSubStepsFileName() != null) {
            subStepsFile = new File(config.getSubStepsFileName());
        }

        final Syntax syntax = SyntaxBuilder.buildSyntax(config.getStepImplementationClasses(), subStepsFile,
                config.isStrict(), config.getNonStrictKeywordPrecedence());

        final TestParameters parameters = new TestParameters(tagmanager, syntax, config.getFeatureFile());

        parameters.setFailParseErrorsImmediately(config.isFastFailParseErrors());
        parameters.init();

        final ExecutionNodeTreeBuilder nodeTreeBuilder = new ExecutionNodeTreeBuilder(parameters);

        // building the tree can throw critical failures if exceptions are found
        rootNode = nodeTreeBuilder.buildExecutionNodeTree(theConfig.getDescription());

        ExecutionContext.put(Scope.SUITE, INotificationDistributor.NOTIFIER_DISTRIBUTOR_KEY, notificationDistributor);

        final String dryRunProperty = System.getProperty(DRY_RUN_KEY);
        boolean dryRun = dryRunProperty != null && Boolean.parseBoolean(dryRunProperty);

        MethodExecutor methodExecutorToUse = dryRun ? new DryRunImplementationCache() : methodExecutor;

        if (dryRun) {
            log.info("**** DRY RUN ONLY **");
        }

        nodeExecutionContext = new RootNodeExecutionContext(notificationDistributor,
                Lists.<SubstepExecutionFailure> newArrayList(), setupAndTearDown, nonFatalTagmanager,
                methodExecutorToUse);

        return rootNode;
    }

    public RootNode run() {

        // TODO - why is this here twice?
        ExecutionContext.put(Scope.SUITE, INotificationDistributor.NOTIFIER_DISTRIBUTOR_KEY, notificationDistributor);

        rootNodeRunner.run(rootNode, nodeExecutionContext);

        if (!nodeExecutionContext.haveTestsBeenRun()) {

            final Throwable t = new IllegalStateException("No tests executed");
            rootNode.getResult().setFailed(t);
            notificationDistributor.notifyNodeFailed(rootNode, t);

            nodeExecutionContext.addFailure(new SubstepExecutionFailure(t, rootNode));
        }

        failures = nodeExecutionContext.getFailures();

        return this.rootNode;
    }

    public List<SubstepExecutionFailure> getFailures() {

        return this.failures;
    }

}

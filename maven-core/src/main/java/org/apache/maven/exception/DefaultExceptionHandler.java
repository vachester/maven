package org.apache.maven.exception;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.plugin.AbstractMojoExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginExecutionException;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.StringUtils;

/*

- test projects for each of these
- how to categorize the problems so that the id of the problem can be match to a page with descriptive help and the test project
- nice little sample projects that could be run in the core as well as integration tests

All Possible Errors
- invalid lifecycle phase (maybe same as bad CLI param, though you were talking about embedder too)
- <module> specified is not found
- malformed settings
- malformed POM
- local repository not writable
- remote repositories not available
- artifact metadata missing
- extension metadata missing
- extension artifact missing
- artifact metadata retrieval problem
- version range violation 
- circular dependency
- artifact missing
- artifact retrieval exception
- md5 checksum doesn't match for local artifact, need to redownload this
- POM doesn't exist for a goal that requires one
- parent POM missing (in both the repository + relative path)
- component not found

Plugins:
- plugin metadata missing
- plugin metadata retrieval problem
- plugin artifact missing
- plugin artifact retrieval problem
- plugin dependency metadata missing
- plugin dependency metadata retrieval problem
- plugin configuration problem
- plugin execution failure due to something that is know to possibly go wrong (like compilation failure)
- plugin execution error due to something that is not expected to go wrong (the compiler executable missing)
- asking to use a plugin for which you do not have a version defined - tools to easily select versions
- goal not found in a plugin (probably could list the ones that are)

 */

//PluginNotFoundException, PluginResolutionException, PluginDescriptorParsingException, CycleDetectedInPluginGraphException;

@Component(role=ExceptionHandler.class)
public class DefaultExceptionHandler
    implements ExceptionHandler
{

    public ExceptionSummary handleException( Throwable exception )
    {
        return handle( "", exception );
    }

    private ExceptionSummary handle( String message, Throwable exception )
    {
        String reference = getReference( exception );

        List<ExceptionSummary> children = null;

        if ( exception instanceof ProjectBuildingException )
        {
            List<ProjectBuildingResult> results = ( (ProjectBuildingException) exception ).getResults();

            children = new ArrayList<ExceptionSummary>();

            for ( ProjectBuildingResult result : results )
            {
                ExceptionSummary child = handle( result );
                if ( child != null )
                {
                    children.add( child );
                }
            }

            message = "The build could not read " + children.size() + " project" + ( children.size() == 1 ? "" : "s" );
        }
        else
        {
            message = getMessage( message, exception );
        }

        return new ExceptionSummary( exception, message, reference, children );
    }

    private ExceptionSummary handle( ProjectBuildingResult result )
    {
        List<ExceptionSummary> children = new ArrayList<ExceptionSummary>();

        for ( ModelProblem problem : result.getProblems() )
        {
            ExceptionSummary child = handle( problem );
            if ( child != null )
            {
                children.add( child );
            }
        }

        if ( children.isEmpty() )
        {
            return null;
        }

        String message =
            "The project " + result.getProjectId() + " (" + result.getPomFile() + ") has " + children.size() + " error"
                + ( children.size() == 1 ? "" : "s" );

        return new ExceptionSummary( null, message, null, children );
    }

    private ExceptionSummary handle( ModelProblem problem )
    {
        if ( ModelProblem.Severity.ERROR.compareTo( problem.getSeverity() ) >= 0 )
        {
            return handle( problem.getMessage(), problem.getException() );
        }
        else
        {
            return null;
        }
    }

    private String getReference( Throwable exception )
    {
        String reference = "";

        if ( exception != null )
        {
            if ( exception instanceof MojoExecutionException )
            {
                reference = MojoExecutionException.class.getSimpleName();
            }
            else if ( exception instanceof MojoFailureException )
            {
                reference = MojoFailureException.class.getSimpleName();
            }
            else if ( exception instanceof LinkageError )
            {
                reference = LinkageError.class.getSimpleName();
            }
            else if ( exception instanceof PluginExecutionException )
            {
                reference = getReference( exception.getCause() );

                if ( StringUtils.isEmpty( reference ) )
                {
                    reference = exception.getClass().getSimpleName();
                }
            }
            else if ( !( exception instanceof RuntimeException ) )
            {
                reference = exception.getClass().getSimpleName();
            }
        }

        if ( StringUtils.isNotEmpty( reference ) && !reference.startsWith( "http:" ) )
        {
            reference = "http://cwiki.apache.org/confluence/display/MAVEN/" + reference;
        }

        return reference;
    }

    private String getMessage( String message, Throwable exception )
    {
        String fullMessage = ( message != null ) ? message : "";

        for ( Throwable t = exception; t != null; t = t.getCause() )
        {
            String exceptionMessage = t.getMessage();

            if ( t instanceof AbstractMojoExecutionException )
            {
                String longMessage = ( (AbstractMojoExecutionException) t ).getLongMessage();
                if ( StringUtils.isNotEmpty( longMessage ) )
                {
                    exceptionMessage = longMessage;
                }
            }

            if ( t instanceof UnknownHostException && !fullMessage.contains( "host" ) )
            {
                if ( fullMessage.length() > 0 )
                {
                    fullMessage += ": ";
                }
                fullMessage += "Unknown host " + exceptionMessage;
            }
            else if ( !fullMessage.contains( exceptionMessage ) )
            {
                if ( fullMessage.length() > 0 )
                {
                    fullMessage += ": ";
                }
                fullMessage += exceptionMessage;
            }
        }

        if ( StringUtils.isEmpty( fullMessage ) && exception != null )
        {
            fullMessage = exception.toString();
        }

        return fullMessage.trim();
    }

}
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.it.compare;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the default ("enhanced") local repository manager so that
 * {@link LocalRepositoryManager#getPathForLocalArtifact} returns a bare filename without any directory,
 * simulating a local repository layout that is not groupId/artifactId/version based.
 * The flat layout is only active when the {@code flatLocalRepository} property is set to {@code true},
 * so that the reference build can install its artifacts using the standard layout.
 */
@Named("flat")
@Singleton
public class FlatLocalRepositoryManagerFactory implements LocalRepositoryManagerFactory {
    static final String PROPERTY = "flatLocalRepository";

    private static final Logger LOGGER = LoggerFactory.getLogger(FlatLocalRepositoryManagerFactory.class);

    private final LocalRepositoryManagerFactory delegate;

    @Inject
    public FlatLocalRepositoryManagerFactory(@Named("enhanced") LocalRepositoryManagerFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public LocalRepositoryManager newInstance(RepositorySystemSession session, LocalRepository repository)
            throws NoLocalRepositoryManagerException {
        final LocalRepositoryManager manager = delegate.newInstance(session, repository);
        if (!isEnabled(session)) {
            return manager;
        }
        LOGGER.info("flat local repository layout enabled");
        return (LocalRepositoryManager) Proxy.newProxyInstance(
                LocalRepositoryManager.class.getClassLoader(),
                new Class<?>[] {LocalRepositoryManager.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        Object result;
                        try {
                            result = method.invoke(manager, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                        if ("getPathForLocalArtifact".equals(method.getName())) {
                            String path = (String) result;
                            String filename = path.substring(path.lastIndexOf('/') + 1);
                            LOGGER.info("flat local repository path for {}: {}", args[0], filename);
                            return filename;
                        }
                        return result;
                    }
                });
    }

    private static boolean isEnabled(RepositorySystemSession session) {
        Object value = session.getConfigProperties().get(PROPERTY);
        if (value == null) {
            value = System.getProperty(PROPERTY);
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @Override
    public float getPriority() {
        return 100;
    }
}

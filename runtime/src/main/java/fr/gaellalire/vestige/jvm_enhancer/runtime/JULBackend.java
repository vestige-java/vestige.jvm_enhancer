/*
 * Copyright 2017 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.gaellalire.vestige.jvm_enhancer.runtime;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.btr.proxy.util.Logger.LogBackEnd;
import com.btr.proxy.util.Logger.LogLevel;

/**
 * @author Gael Lalire
 */
public class JULBackend implements LogBackEnd {

    private static final Logger LOGGER = Logger.getAnonymousLogger();

    public void log(final Class<?> clazz, final LogLevel loglevel, final String msg, final Object... params) {
        Level level;
        switch (loglevel) {
        case TRACE:
            level = Level.FINEST;
            break;
        case DEBUG:
            level = Level.FINE;
            break;
        case INFO:
            level = Level.INFO;
            break;
        case WARNING:
            level = Level.WARNING;
            break;
        case ERROR:
            level = Level.SEVERE;
            break;
        default:
            throw new Error("Unknown level " + loglevel);
        }
        LogRecord logRecord = new LogRecord(level, msg);
        logRecord.setLoggerName(clazz.getName());
        logRecord.setParameters(params);
        LOGGER.log(logRecord);
    }

    public boolean isLogginEnabled(final LogLevel logLevel) {
        return true;
    }

}

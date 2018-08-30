/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.jexl3.internal;

import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.parser.ASTJexlScript;

import org.apache.commons.jexl3.parser.JexlNode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>A JexlScript implementation.</p>
 * @since 1.1
 */
public class Script implements JexlScript, JexlExpression {
    /**
     * The engine for this expression.
     */
    protected final Engine jexl;
    /**
     * Original expression stripped from leading and trailing spaces.
     */
    protected final String source;
    /**
     * The resulting AST we can interpret.
     */
    protected final ASTJexlScript script;
    /**
     * The engine version (as class loader change count) that last evaluated this script.
     */
    protected int version;

    /**
     * @return the script AST
     */
    protected ASTJexlScript getScript() {
        return script;
    }

    /**
     * Do not let this be generally instantiated with a 'new'.
     *
     * @param engine the interpreter to evaluate the expression
     * @param expr   the expression source.
     * @param ref    the parsed expression.
     */
    protected Script(Engine engine, String expr, ASTJexlScript ref) {
        jexl = engine;
        source = expr;
        script = ref;
        version = jexl.getUberspect().getVersion();
    }

    /**
     * Checks that this script cached methods (wrt introspection) matches the engine version.
     * <p>
     * If the engine class loader has changed since we last evaluated this script, the script local cache
     * is invalidated to drop references to obsolete methods. It is not strictly necessary since the tryExecute
     * will fail because the class wont match but it seems cleaner nevertheless.
     * </p>
     */
    protected void checkCacheVersion() {
        int uberVersion = jexl.getUberspect().getVersion();
        if (version != uberVersion) {
            // version 0 of the uberSpect is an illusion due to order of construction; no need to clear cache
            if (version > 0) {
                script.clearCache();
            }
            version = uberVersion;
        }
    }

    /**
     * Creates this script frame for evaluation.
     * @param args the arguments to bind to parameters
     * @return the frame (may be null)
     */
    protected Scope.Frame createFrame(Object[] args) {
        return script.createFrame(args);
    }

    /**
     * Creates this script interpreter.
     * @param context the context
     * @param frame the calling frame
     * @return  the interpreter
     */
    protected Interpreter createInterpreter(JexlContext context, Scope.Frame frame) {
        return jexl.createInterpreter(context, frame);
    }

    /**
     * @return the engine that created this script
     */
    public JexlEngine getEngine() {
        return jexl;
    }

    @Override
    public String getSourceText() {
        return source;
    }

    @Override
    public String getParsedText() {
        return getParsedText(2);
    }

    @Override
    public String getParsedText(int indent) {
        Debugger debug = new Debugger();
        debug.setIndentation(indent);
        debug.debug(script, false);
        return debug.toString();
    }

    @Override
    public int hashCode() {
        // CSOFF: Magic number
        int hash = 17;
        hash = 31 * hash + (this.jexl != null ? this.jexl.hashCode() : 0);
        hash = 31 * hash + (this.source != null ? this.source.hashCode() : 0);
        return hash;
        // CSON: Magic number
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Script other = (Script) obj;
        if (this.jexl != other.jexl) {
            return false;
        }
        if ((this.source == null) ? (other.source != null) : !this.source.equals(other.source)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        CharSequence src = source;
        if (src == null) {
            Debugger debug = new Debugger();
            debug.debug(script, false);
            src = debug.toString();
        }
        return src.toString();
    }

    @Override
    public Object evaluate(JexlContext context) {
        return execute(context);
    }

    @Override
    public Object execute(JexlContext context) {
        checkCacheVersion();
        Scope.Frame frame = createFrame(null);
        Interpreter interpreter = createInterpreter(context, frame);
        return interpreter.interpret(script);
    }

    @Override
    public Object execute(JexlContext context, Object... args) {
        checkCacheVersion();
        Scope.Frame frame = createFrame(scriptArgs(args));
        Interpreter interpreter = createInterpreter(context, frame);
        return interpreter.interpret(script);
    }

    /**
     * @return the script parameter list with regard to vararg option
     */

    protected Object[] scriptArgs(Object[] args) {
        return args != null && args.length > 0 ? scriptArgs(0, args) : args;
    }

    protected Object[] scriptArgs(int curried, Object[] args) {

        boolean varArgs = script.isVarArgs();
        int argCount = script.getArgCount() - curried;
        Object[] params = null;

        if (varArgs && args != null && args.length > 0 && args.length >= argCount) {

            if (argCount > 0) {
               params = new Object[argCount];
               for (int i = 0; i < argCount - 1; i++)
                   params[i] = args[i];
               int varArgCount = args.length - argCount + 1;
               Object[] varg = new Object[varArgCount];
               for (int i = 0; i < varArgCount; i++)
                   varg[i] = args[argCount + i - 1];
               params[argCount-1] = varg;
            } else {
               params = new Object[] {args};
            }
        } else {
            params = args;
        }

        return params;
    }

    /**
     * A script whose parameters are (partially) bound.
     */
    public static class Curried extends Script {
        /** The evaluation frame. */
        private final Scope.Frame frame;

        /** The number of arguments being curried. */
        private final int argCount;

        /**
         * Creates a curried version of this script.
         * @param base the base script
         * @param args the arguments
         */
        protected Curried(Script base, Object[] args) {
            super(base.jexl, base.source, base.script);

            if (base instanceof Curried) {
                Scope.Frame sf = ((Curried) base).frame;

                boolean varArgs = script.isVarArgs();
                int baseArgCount = ((Curried) base).argCount;

                if (varArgs) {
                    if (baseArgCount >= script.getArgCount()) {
                       frame = createNewVarArgFrame(sf, args);
                    } else {
                       frame = sf.assign(scriptArgs(baseArgCount, args));
                    }
                } else {
                    frame = sf.assign(args);
                }
                argCount = baseArgCount + args.length;
            } else {
                frame = script.createFrame(scriptArgs(args));
                argCount = args.length;
            }
        }

        protected Scope.Frame createNewVarArgFrame(Scope.Frame sf, Object[] args) {

            if (args != null && args.length > 0) {
               int varArgPos = script.getArgCount() - 1;
               Scope.Frame frame = sf.clone();

               Object[] carg = (Object[]) frame.get(varArgPos);
               Object[] varg = new Object[carg.length + args.length];

               System.arraycopy(carg, 0, varg, 0, carg.length);
               System.arraycopy(args, 0, varg, carg.length, args.length);
               frame.set(varArgPos, varg);

               return frame;
            } else {
               return sf;
            }
        }

       /**
        * Gets remaining curried lambda parameters.
        * @return the parameters or null
        * @since 3.2
        */
        @Override
        public String[] getParameters() {

            String[] scriptParams = super.getParameters();

            if (scriptParams == null || scriptParams.length == 0)
              return scriptParams;

            boolean varArgs = script.isVarArgs();

            if (argCount >= scriptParams.length) {
               return varArgs ? new String[] {scriptParams[scriptParams.length - 1]} : null;
            } else {
               String[] result = new String[scriptParams.length - argCount];
               System.arraycopy(scriptParams, argCount, result, 0, scriptParams.length - argCount);
               return result;
            }
        }

        @Override
        protected Scope.Frame createFrame(Object[] args) {
            return frame != null? frame.assign(args) : super.createFrame(args);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public Object execute(JexlContext context) {
            return execute(context, (Object[])null);
        }

        @Override
        public Object execute(JexlContext context, Object... args) {
            Scope.Frame callFrame = null;
            if (frame != null) {

                boolean varArgs = script.isVarArgs();

                if (varArgs) {

                    if (argCount >= script.getArgCount()) {
                       callFrame = createNewVarArgFrame(frame, args);
                    } else {
                       callFrame = frame.assign(scriptArgs(argCount, args));
                    }

                } else {
                    callFrame = frame.assign(args);
                }
            } else {
                callFrame = script.createFrame(scriptArgs(args));
            }
            Interpreter interpreter = jexl.createInterpreter(context, callFrame);
            JexlNode block = script.jjtGetChild(script.jjtGetNumChildren() - 1);
            return interpreter.interpret(block);
        }
    }

    @Override
    public JexlScript curry(Object... args) {
        String[] parms = script.getParameters();
        if (parms == null || parms.length == 0 || args == null || args.length == 0)
            return this;
        return new Curried(this, args);
    }

    /**
     * Gets this script parameters.
     * @return the parameters or null
     * @since 3.0
     */
    @Override
    public String[] getParameters() {
        return script.getParameters();
    }

    /**
     * Returns true if this script support variable argument.
     * @return boolean
     * @since 3.2
     */
    @Override
    public boolean isVarArgs() {
        return script.isVarArgs();
    }

    /**
     * Gets this script local variables.
     * @return the local variables or null
     */
    @Override
    public String[] getLocalVariables() {
        return script.getLocalVariables();
    }

    /**
     * Gets this script variables.
     * <p>Note that since variables can be in an ant-ish form (ie foo.bar.quux), each variable is returned as
     * a list of strings where each entry is a fragment of the variable ({"foo", "bar", "quux"} in the example.</p>
     * @return the variables or null
     */
    @Override
    public Set<List<String>> getVariables() {
        return jexl.getVariables(script);
    }

    /**
     * Get this script pragmas
     * <p>Pragma keys are ant-ish variables, their values are scalar literals..
     * @return the pragmas
     */
    @Override
    public Map<String, Object> getPragmas() {
        return script.getPragmas();
    }

    /**
     * Creates a Callable from this script.
     * <p>This allows to submit it to an executor pool and provides support for asynchronous calls.</p>
     * <p>The interpreter will handle interruption/cancellation gracefully if needed.</p>
     * @param context the context
     * @return the callable
     */
    @Override
    public Callable callable(JexlContext context) {
        return callable(context, (Object[]) null);
    }

    /**
     * Creates a Callable from this script.
     * <p>This allows to submit it to an executor pool and provides support for asynchronous calls.</p>
     * <p>The interpreter will handle interruption/cancellation gracefully if needed.</p>
     * @param context the context
     * @param args    the script arguments
     * @return the callable
     */
    @Override
    public Callable callable(JexlContext context, Object... args) {
        return new Callable(jexl.createInterpreter(context, script.createFrame(scriptArgs(args))));
    }

    /**
     * Implements the Future and Callable interfaces to help delegation.
     */
    public class Callable implements java.util.concurrent.Callable<Object> {
        /** The actual interpreter. */
        protected final Interpreter interpreter;
        /** Use interpreter as marker for not having run. */
        protected volatile Object result;

        /**
         * The base constructor.
         * @param intrprtr the interpreter to use
         */
        protected Callable(Interpreter intrprtr) {
            this.interpreter = intrprtr;
            this.result = intrprtr;
        }

        /**
         * Run the interpreter.
         * @return the evaluation result
         */
        protected Object interpret() {
            return interpreter.interpret(script);
        }

        @Override
        public Object call() throws Exception {
            synchronized(this) {
                if (result == interpreter) {
                    checkCacheVersion();
                    result = interpret();
                }
                return result;
            }
        }

        /**
         * Soft cancel the execution.
         * @return true if cancel was successful, false otherwise
         */
        public boolean cancel() {
            return interpreter.cancel();
        }

        /**
         * @return true if evaluation was cancelled, false otherwise
         */
        public boolean isCancelled() {
            return interpreter.isCancelled();
        }

        /**
         * @return true if interruption will throw a JexlException.Cancel, false otherwise
         */
        public boolean isCancellable() {
            return interpreter.isCancellable();
        }
    }
}
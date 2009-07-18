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

package org.apache.commons.jexl;

import org.apache.commons.jexl.parser.SimpleNode;

/**
 * Instances of ExpressionImpl are created by the {@link JexlEngine},
 * and this is the default implementation of the {@link Expression} interface.
 *
 * @since 1.0
 * @author <a href="mailto:geirm@apache.org">Geir Magnusson Jr.</a>
 * @version $Id$
 */
class ExpressionImpl implements Expression {
    /** The engine for this expression. */
    protected final JexlEngine jexl;
    /**
     * Original expression. This is just a 'snippet', not a valid statement
     * (i.e. foo.bar() vs foo.bar();
     */
    protected final String expression;

    /**
     * The resulting AST we can interpret.
     */
    protected final SimpleNode node;


    /**
     * Do not let this be generally instantiated with a 'new'.
     *
     * @param engine the interpreter to evaluate the expression
     * @param expr the expression.
     * @param ref the parsed expression.
     */
    ExpressionImpl(JexlEngine engine, String expr, SimpleNode ref) {
        expression = expr;
        node = ref;
        jexl = engine;
    }

    /**
     * {@inheritDoc}
     */
    public Object evaluate(JexlContext context) {
        Interpreter interpreter = jexl.createInterpreter(context);
        return interpreter.interpret(node);
    }

    /**
     * {@inheritDoc}
     */
    public String dump() {
        Debugger debug = new Debugger();
        return debug.debug(node)? debug.toString() : "/*?*/";
    }

    /**
     * {@inheritDoc}
     */
    public String getExpression() {
        return expression;
    }

    /**
     * Provide a string representation of the expression.
     *
     * @return the expression or blank if it's null.
     */
    @Override
    public String toString() {
        String expr = getExpression();
        return expr == null ? "" : expr;
    }

}
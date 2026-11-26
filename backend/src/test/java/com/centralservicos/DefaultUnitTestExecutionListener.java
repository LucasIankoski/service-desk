package com.centralservicos;

import com.centralservicos.shared.TenantContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Define a unidade padrão em cada teste Spring, antes de a transação do teste abrir (a ordem precede o
 * {@code TransactionalTestExecutionListener}). Testes de isolamento podem entrar em outra unidade com
 * {@link TenantContext#enter(java.util.UUID)}.
 */
public class DefaultUnitTestExecutionListener implements TestExecutionListener, Ordered {

    private static final String SCOPE = DefaultUnitTestExecutionListener.class.getName() + ".scope";

    @Override
    public void beforeTestMethod(TestContext testContext) {
        testContext.setAttribute(SCOPE, TenantContext.enter(TestUnits.DEFAULT_UNIT_ID));
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        if (testContext.removeAttribute(SCOPE) instanceof TenantContext.Scope scope) {
            scope.close();
        }
    }

    @Override
    public int getOrder() {
        return 1_500;
    }
}

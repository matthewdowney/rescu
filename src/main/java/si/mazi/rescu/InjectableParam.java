package si.mazi.rescu;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import javax.ws.rs.HeaderParam;

/**
 * Signals that a {@link Proxy} implementation of an interface is responsible
 * for injecting the indicated parameter. (When applied to an interface, that
 * parameter should be injected into each method in the interface. When applied
 * to a method, the parameter should only be injected for that method.) This
 * means that the {@link InvocationHandler} provided must have some way of
 * generating & injecting the described parameter.
 * 
 * For example, the interface
 * 
 * <pre>
 * <code>
 * public interface RESTApi {
 *    void getAccountInfo(@HeaderParam("Signature") ParamsDigest signature);
 *    void moveFunds(String fromWalletId, String toAddress, @HeaderParam("Signature") ParamsDigest signature, @HeaderParam("WalletPassword") String walletPassword);
 * }
 * </code>
 * </pre>
 * 
 * Could be annotated:
 * 
 * <pre>
 * <code>
 * &#64;InjectableParam(
 *    annotations = {HeaderParam.class}, 
 *    type = ParamsDigest.class, 
 *    name = "Signature"
 * )
 * public interface RESTApi {
 *    void getAccountInfo();
 *    
 *    &#64;InjectableParam(
 *       annotations = {HeaderParam.class}, 
 *       type = String.class, 
 *       name = "WalletPassword"
 *    )
 *    void moveFunds(String fromWalletId, String toAddress);
 * }
 * </code>
 * </pre>
 * 
 * Indicating that each method in the interface needs a signature, of type
 * {@link ParamsDigest}, annotated as a {@link HeaderParam}, and that to call
 * <code>moveFunds</code> a String typed walletPassword annotated as a
 * {@link HeaderParam} is required.
 */
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(InjectableParams.class)
public @interface InjectableParam {
  Class<? extends Annotation>[] annotations();

  Class<?> type();

  String name();
}

/*
 * Copyright (C) 2013 Matija Mazi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package si.mazi.rescu;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Matija Mazi <br>
 */
public final class AnnotationUtils {

    private AnnotationUtils() throws InstantiationException {
        throw new InstantiationException("This class is not for instantiation");
    }

    static <T extends Annotation> String getValueOrNull(Class<T> annotationClass, Annotation ann) {

        if (!annotationClass.isInstance(ann)) {
            return null;
        }
        try {
            return (String) ann.getClass().getMethod("value").invoke(ann);
        } catch (Exception e) {
            throw new RuntimeException("Can't access element 'value' in  " + annotationClass + ". This is probably a bug in rescu.", e);
        }
    }

    static <A extends Annotation> A getFromMethodOrClass(Method method, Class<A> annotationClass) {

        A methodAnn = method.getAnnotation(annotationClass);
        if (methodAnn != null) {
            return methodAnn;
        }
        for (Class<?> cls = method.getDeclaringClass(); cls != null; cls = cls.getSuperclass()) {
            if (cls.isAnnotationPresent(annotationClass)) {
                return cls.getAnnotation(annotationClass);
            }
        }
        for (Class<?> intf : method.getDeclaringClass().getInterfaces()) {
            if (intf.isAnnotationPresent(annotationClass)) {
                return intf.getAnnotation(annotationClass);
            }
        }
        return null;
    }
    
    static InjectableParam[] getInjectablesFromMethodAndClass(Method method) {
      // Get all instances of the InjectableParams container that might be present on the method/superclasses
      List<InjectableParams> injectablesContainer = AnnotationUtils.getAllFromMethodAndClass(method,
          InjectableParams.class);
      
      // Get all instances of the single InjectableParam annotation that might be present on the method/superclasses
      List<InjectableParam> injectableParam = AnnotationUtils.getAllFromMethodAndClass(method, InjectableParam.class);

      // Concatenate them all together into a flat array of InjectableParam
      InjectableParam[] injectables = new InjectableParam[0];
      for (int i = 0; i < injectablesContainer.size(); ++i) {
        injectables = Utils.arrayConcat(injectables, injectablesContainer.get(i).value());
      }
      InjectableParam[] intermediate = injectableParam.toArray(new InjectableParam[0]);
      return Utils.arrayConcat(injectables, intermediate);
    }
    
    @SuppressWarnings("unchecked")
    static <A extends Annotation> List<A> getAllFromMethodAndClass(Method method, Class<A> annotationClass) {
        List<A> annotations = new ArrayList<>();
        A annotation = method.getAnnotation(annotationClass);
        if (annotation != null)
          annotations.add(annotation);
        for (Class<?> cls = method.getDeclaringClass(); cls != null; cls = cls.getSuperclass()) {
            if (cls.isAnnotationPresent(annotationClass)) {
                annotations.add(cls.getAnnotation(annotationClass));
            }
        }
        for (Class<?> intf : method.getDeclaringClass().getInterfaces()) {
            if (intf.isAnnotationPresent(annotationClass)) {
                annotations.add(intf.getAnnotation(annotationClass));
            }
        }
        return annotations;
    }

    /**
     * @return a map of annotations on the method that belong to the given collection.
     */
    static Map<Class<? extends Annotation>, Annotation> getMethodAnnotationMap(Method method, Collection<Class<? extends Annotation>> annotationClasses) {
        Annotation[] methodAnnotations = method.getAnnotations();
        Map<Class<? extends Annotation>, Annotation> methodAnnotationMap = new HashMap<>();
        for (Annotation methodAnnotation : methodAnnotations) {
            methodAnnotationMap.put(methodAnnotation.annotationType(), methodAnnotation);
        }
        methodAnnotationMap.keySet().retainAll(annotationClasses);
        return methodAnnotationMap;
    }
}

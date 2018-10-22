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
package com.mawujun.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;



public class AnnotationUtils extends org.apache.commons.lang3.AnnotationUtils{


	/** The attribute name for annotations with a single element */
	static final String VALUE = "value";

	private static final Map<Class<?>, Boolean> annotatedInterfaceCache = new WeakHashMap<Class<?>, Boolean>();


	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the supplied
	 * Method, Constructor or Field. Meta-annotations will be searched if the annotation
	 * is not declared locally on the supplied element.
	 * @param ae the Method, Constructor or Field from which to get the annotation
	 * @param annotationType the annotation class to look for, both locally and as a meta-annotation
	 * @return the matching annotation or {@code null} if not found
	 * @since 3.1
	 */
	public static <T extends Annotation> T getAnnotation(AnnotatedElement ae, Class<T> annotationType) {
		T ann = ae.getAnnotation(annotationType);
		if (ann == null) {
			for (Annotation metaAnn : ae.getAnnotations()) {
				ann = metaAnn.annotationType().getAnnotation(annotationType);
				if (ann != null) {
					break;
				}
			}
		}
		return ann;
	}

//	/**
//	 * Get all {@link Annotation Annotations} from the supplied {@link Method}.
//	 * <p>Correctly handles bridge {@link Method Methods} generated by the compiler.
//	 * @param method the method to look for annotations on
//	 * @return the annotations found
//	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
//	 */
//	public static Annotation[] getAnnotations(Method method) {
//		return BridgeMethodResolver.findBridgedMethod(method).getAnnotations();
//	}
//
//	/**
//	 * Get a single {@link Annotation} of {@code annotationType} from the supplied {@link Method}.
//	 * <p>Correctly handles bridge {@link Method Methods} generated by the compiler.
//	 * @param method the method to look for annotations on
//	 * @param annotationType the annotation class to look for
//	 * @return the annotations found
//	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
//	 */
//	public static <A extends Annotation> A getAnnotation(Method method, Class<A> annotationType) {
//		Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
//		A ann = resolvedMethod.getAnnotation(annotationType);
//		if (ann == null) {
//			for (Annotation metaAnn : resolvedMethod.getAnnotations()) {
//				ann = metaAnn.annotationType().getAnnotation(annotationType);
//				if (ann != null) {
//					break;
//				}
//			}
//		}
//		return ann;
//	}

	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the supplied {@link Method},
	 * traversing its super methods if no annotation can be found on the given method itself.
	 * <p>Annotations on methods are not inherited by default, so we need to handle this explicitly.
	 * @param method the method to look for annotations on
	 * @param annotationType the annotation class to look for
	 * @return the annotation found, or {@code null} if none found
	 */
	public static <A extends Annotation> A findAnnotation(Method method, Class<A> annotationType) {
		A annotation = getAnnotation(method, annotationType);
		Class<?> cl = method.getDeclaringClass();
		if (annotation == null) {
			annotation = searchOnInterfaces(method, annotationType, cl.getInterfaces());
		}
		while (annotation == null) {
			cl = cl.getSuperclass();
			if (cl == null || cl == Object.class) {
				break;
			}
			try {
				Method equivalentMethod = cl.getDeclaredMethod(method.getName(), method.getParameterTypes());
				annotation = getAnnotation(equivalentMethod, annotationType);
			}
			catch (NoSuchMethodException ex) {
				// No equivalent method found
			}
			if (annotation == null) {
				annotation = searchOnInterfaces(method, annotationType, cl.getInterfaces());
			}
		}
		return annotation;
	}

	private static <A extends Annotation> A searchOnInterfaces(Method method, Class<A> annotationType, Class<?>[] ifcs) {
		A annotation = null;
		for (Class<?> iface : ifcs) {
			if (isInterfaceWithAnnotatedMethods(iface)) {
				try {
					Method equivalentMethod = iface.getMethod(method.getName(), method.getParameterTypes());
					annotation = getAnnotation(equivalentMethod, annotationType);
				}
				catch (NoSuchMethodException ex) {
					// Skip this interface - it doesn't have the method...
				}
				if (annotation != null) {
					break;
				}
			}
		}
		return annotation;
	}

	private static boolean isInterfaceWithAnnotatedMethods(Class<?> iface) {
		synchronized (annotatedInterfaceCache) {
			Boolean flag = annotatedInterfaceCache.get(iface);
			if (flag != null) {
				return flag;
			}
			boolean found = false;
			for (Method ifcMethod : iface.getMethods()) {
				if (ifcMethod.getAnnotations().length > 0) {
					found = true;
					break;
				}
			}
			annotatedInterfaceCache.put(iface, found);
			return found;
		}
	}

	/**
	 * Find a single {@link Annotation} of {@code annotationType} from the supplied {@link Class},
	 * traversing its interfaces and superclasses if no annotation can be found on the given class itself.
	 * <p>This method explicitly handles class-level annotations which are not declared as
	 * {@link java.lang.annotation.Inherited inherited} <i>as well as annotations on interfaces</i>.
	 * <p>The algorithm operates as follows: Searches for an annotation on the given class and returns
	 * it if found. Else searches all interfaces that the given class declares, returning the annotation
	 * from the first matching candidate, if any. Else proceeds with introspection of the superclass
	 * of the given class, checking the superclass itself; if no annotation found there, proceeds
	 * with the interfaces that the superclass declares. Recursing up through the entire superclass
	 * hierarchy if no match is found.
	 * @param clazz the class to look for annotations on
	 * @param annotationType the annotation class to look for
	 * @return the annotation found, or {@code null} if none found
	 */
	public static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType) {
		Assert.notNull(clazz, "Class must not be null");
		A annotation = clazz.getAnnotation(annotationType);
		if (annotation != null) {
			return annotation;
		}
		for (Class<?> ifc : clazz.getInterfaces()) {
			annotation = findAnnotation(ifc, annotationType);
			if (annotation != null) {
				return annotation;
			}
		}
		if (!Annotation.class.isAssignableFrom(clazz)) {
			for (Annotation ann : clazz.getAnnotations()) {
				annotation = findAnnotation(ann.annotationType(), annotationType);
				if (annotation != null) {
					return annotation;
				}
			}
		}
		Class<?> superClass = clazz.getSuperclass();
		if (superClass == null || superClass == Object.class) {
			return null;
		}
		return findAnnotation(superClass, annotationType);
	}

	/**
	 * Find the first {@link Class} in the inheritance hierarchy of the specified {@code clazz}
	 * (including the specified {@code clazz} itself) which declares an annotation for the
	 * specified {@code annotationType}, or {@code null} if not found. If the supplied
	 * {@code clazz} is {@code null}, {@code null} will be returned.
	 * <p>If the supplied {@code clazz} is an interface, only the interface itself will be checked;
	 * the inheritance hierarchy for interfaces will not be traversed.
	 * <p>The standard {@link Class} API does not provide a mechanism for determining which class
	 * in an inheritance hierarchy actually declares an {@link Annotation}, so we need to handle
	 * this explicitly.
	 * @param annotationType the Class object corresponding to the annotation type
	 * @param clazz the Class object corresponding to the class on which to check for the annotation,
	 * or {@code null}
	 * @return the first {@link Class} in the inheritance hierarchy of the specified {@code clazz}
	 * which declares an annotation for the specified {@code annotationType}, or {@code null}
	 * if not found
	 * @see Class#isAnnotationPresent(Class)
	 * @see Class#getDeclaredAnnotations()
	 * @see #findAnnotationDeclaringClassForTypes(List, Class)
	 * @see #isAnnotationDeclaredLocally(Class, Class)
	 */
	public static Class<?> findAnnotationDeclaringClass(Class<? extends Annotation> annotationType, Class<?> clazz) {
		Assert.notNull(annotationType, "Annotation type must not be null");
		if (clazz == null || clazz.equals(Object.class)) {
			return null;
		}
		return (isAnnotationDeclaredLocally(annotationType, clazz)) ? clazz : findAnnotationDeclaringClass(
			annotationType, clazz.getSuperclass());
	}

	/**
	 * Find the first {@link Class} in the inheritance hierarchy of the specified
	 * {@code clazz} (including the specified {@code clazz} itself) which declares
	 * at least one of the specified {@code annotationTypes}, or {@code null} if
	 * none of the specified annotation types could be found.
	 * <p>If the supplied {@code clazz} is {@code null}, {@code null} will be
	 * returned.
	 * <p>If the supplied {@code clazz} is an interface, only the interface itself
	 * will be checked; the inheritance hierarchy for interfaces will not be traversed.
	 * <p>The standard {@link Class} API does not provide a mechanism for determining
	 * which class in an inheritance hierarchy actually declares one of several
	 * candidate {@linkplain Annotation annotations}, so we need to handle this
	 * explicitly.
	 * @param annotationTypes the list of Class objects corresponding to the
	 * annotation types
	 * @param clazz the Class object corresponding to the class on which to check
	 * for the annotations, or {@code null}
	 * @return the first {@link Class} in the inheritance hierarchy of the specified
	 * {@code clazz} which declares an annotation of at least one of the specified
	 * {@code annotationTypes}, or {@code null} if not found
	 * @see Class#isAnnotationPresent(Class)
	 * @see Class#getDeclaredAnnotations()
	 * @see #findAnnotationDeclaringClass(Class, Class)
	 * @see #isAnnotationDeclaredLocally(Class, Class)
	 * @since 3.2.2
	 */
	public static Class<?> findAnnotationDeclaringClassForTypes(List<Class<? extends Annotation>> annotationTypes,
			Class<?> clazz) {
		Assert.notEmpty(annotationTypes, "The list of annotation types must not be empty");
		if (clazz == null || clazz.equals(Object.class)) {
			return null;
		}

		for (Class<? extends Annotation> annotationType : annotationTypes) {
			if (isAnnotationDeclaredLocally(annotationType, clazz)) {
				return clazz;
			}
		}

		return findAnnotationDeclaringClassForTypes(annotationTypes, clazz.getSuperclass());
	}

	/**
	 * Determine whether an annotation for the specified {@code annotationType} is
	 * declared locally on the supplied {@code clazz}. The supplied {@link Class}
	 * may represent any type.
	 * <p>Note: This method does <strong>not</strong> determine if the annotation is
	 * {@linkplain java.lang.annotation.Inherited inherited}. For greater clarity
	 * regarding inherited annotations, consider using
	 * {@link #isAnnotationInherited(Class, Class)} instead.
	 * @param annotationType the Class object corresponding to the annotation type
	 * @param clazz the Class object corresponding to the class on which to check for the annotation
	 * @return {@code true} if an annotation for the specified {@code annotationType}
	 * is declared locally on the supplied {@code clazz}
	 * @see Class#getDeclaredAnnotations()
	 * @see #isAnnotationInherited(Class, Class)
	 */
	public static boolean isAnnotationDeclaredLocally(Class<? extends Annotation> annotationType, Class<?> clazz) {
		Assert.notNull(annotationType, "Annotation type must not be null");
		Assert.notNull(clazz, "Class must not be null");
		boolean declaredLocally = false;
		for (Annotation annotation : clazz.getDeclaredAnnotations()) {
			if (annotation.annotationType().equals(annotationType)) {
				declaredLocally = true;
				break;
			}
		}
		return declaredLocally;
	}

	/**
	 * Determine whether an annotation for the specified {@code annotationType} is present
	 * on the supplied {@code clazz} and is {@linkplain java.lang.annotation.Inherited inherited}
	 * (i.e., not declared locally for the class).
	 * <p>If the supplied {@code clazz} is an interface, only the interface itself will be checked.
	 * In accordance with standard meta-annotation semantics, the inheritance hierarchy for interfaces
	 * will not be traversed. See the {@linkplain java.lang.annotation.Inherited Javadoc} for the
	 * {@code @Inherited} meta-annotation for further details regarding annotation inheritance.
	 * @param annotationType the Class object corresponding to the annotation type
	 * @param clazz the Class object corresponding to the class on which to check for the annotation
	 * @return {@code true} if an annotation for the specified {@code annotationType} is present
	 * on the supplied {@code clazz} and is <em>inherited</em>
	 * @see Class#isAnnotationPresent(Class)
	 * @see #isAnnotationDeclaredLocally(Class, Class)
	 */
	public static boolean isAnnotationInherited(Class<? extends Annotation> annotationType, Class<?> clazz) {
		Assert.notNull(annotationType, "Annotation type must not be null");
		Assert.notNull(clazz, "Class must not be null");
		return (clazz.isAnnotationPresent(annotationType) && !isAnnotationDeclaredLocally(annotationType, clazz));
	}

//	/**
//	 * Retrieve the given annotation's attributes as a Map, preserving all attribute types
//	 * as-is.
//	 * <p>Note: As of Spring 3.1.1, the returned map is actually an
//	 * {@link AnnotationAttributes} instance, however the Map signature of this method has
//	 * been preserved for binary compatibility.
//	 * @param annotation the annotation to retrieve the attributes for
//	 * @return the Map of annotation attributes, with attribute names as keys and
//	 * corresponding attribute values as values
//	 */
//	public static Map<String, Object> getAnnotationAttributes(Annotation annotation) {
//		return getAnnotationAttributes(annotation, false, false);
//	}
//
//	/**
//	 * Retrieve the given annotation's attributes as a Map. Equivalent to calling
//	 * {@link #getAnnotationAttributes(Annotation, boolean, boolean)} with
//	 * the {@code nestedAnnotationsAsMap} parameter set to {@code false}.
//	 * <p>Note: As of Spring 3.1.1, the returned map is actually an
//	 * {@link AnnotationAttributes} instance, however the Map signature of this method has
//	 * been preserved for binary compatibility.
//	 * @param annotation the annotation to retrieve the attributes for
//	 * @param classValuesAsString whether to turn Class references into Strings (for
//	 * compatibility with {@link org.springframework.core.type.AnnotationMetadata} or to
//	 * preserve them as Class references
//	 * @return the Map of annotation attributes, with attribute names as keys and
//	 * corresponding attribute values as values
//	 */
//	public static Map<String, Object> getAnnotationAttributes(Annotation annotation, boolean classValuesAsString) {
//		return getAnnotationAttributes(annotation, classValuesAsString, false);
//	}
//
//	/**
//	 * Retrieve the given annotation's attributes as an {@link AnnotationAttributes}
//	 * map structure. Implemented in Spring 3.1.1 to provide fully recursive annotation
//	 * reading capabilities on par with that of the reflection-based
//	 * {@link org.springframework.core.type.StandardAnnotationMetadata}.
//	 * @param annotation the annotation to retrieve the attributes for
//	 * @param classValuesAsString whether to turn Class references into Strings (for
//	 * compatibility with {@link org.springframework.core.type.AnnotationMetadata} or to
//	 * preserve them as Class references
//	 * @param nestedAnnotationsAsMap whether to turn nested Annotation instances into
//	 * {@link AnnotationAttributes} maps (for compatibility with
//	 * {@link org.springframework.core.type.AnnotationMetadata} or to preserve them as
//	 * Annotation instances
//	 * @return the annotation attributes (a specialized Map) with attribute names as keys
//	 * and corresponding attribute values as values
//	 * @since 3.1.1
//	 */
//	public static AnnotationAttributes getAnnotationAttributes(Annotation annotation, boolean classValuesAsString,
//			boolean nestedAnnotationsAsMap) {
//
//		AnnotationAttributes attrs = new AnnotationAttributes();
//		Method[] methods = annotation.annotationType().getDeclaredMethods();
//		for (Method method : methods) {
//			if (method.getParameterTypes().length == 0 && method.getReturnType() != void.class) {
//				try {
//					Object value = method.invoke(annotation);
//					if (classValuesAsString) {
//						if (value instanceof Class) {
//							value = ((Class<?>) value).getName();
//						}
//						else if (value instanceof Class[]) {
//							Class<?>[] clazzArray = (Class[]) value;
//							String[] newValue = new String[clazzArray.length];
//							for (int i = 0; i < clazzArray.length; i++) {
//								newValue[i] = clazzArray[i].getName();
//							}
//							value = newValue;
//						}
//					}
//					if (nestedAnnotationsAsMap && value instanceof Annotation) {
//						attrs.put(method.getName(),
//							getAnnotationAttributes((Annotation) value, classValuesAsString, nestedAnnotationsAsMap));
//					}
//					else if (nestedAnnotationsAsMap && value instanceof Annotation[]) {
//						Annotation[] realAnnotations = (Annotation[]) value;
//						AnnotationAttributes[] mappedAnnotations = new AnnotationAttributes[realAnnotations.length];
//						for (int i = 0; i < realAnnotations.length; i++) {
//							mappedAnnotations[i] = getAnnotationAttributes(realAnnotations[i], classValuesAsString,
//								nestedAnnotationsAsMap);
//						}
//						attrs.put(method.getName(), mappedAnnotations);
//					}
//					else {
//						attrs.put(method.getName(), value);
//					}
//				}
//				catch (Exception ex) {
//					throw new IllegalStateException("Could not obtain annotation attribute values", ex);
//				}
//			}
//		}
//		return attrs;
//	}

	/**
	 * Retrieve the <em>value</em> of the {@code &quot;value&quot;} attribute of a
	 * single-element Annotation, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the value
	 * @return the attribute value, or {@code null} if not found
	 * @see #getValue(Annotation, String)
	 */
	public static Object getValue(Annotation annotation) {
		return getValue(annotation, VALUE);
	}

	/**
	 * Retrieve the <em>value</em> of a named Annotation attribute, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the value
	 * @param attributeName the name of the attribute value to retrieve
	 * @return the attribute value, or {@code null} if not found
	 * @see #getValue(Annotation)
	 */
	public static Object getValue(Annotation annotation, String attributeName) {
		try {
			Method method = annotation.annotationType().getDeclaredMethod(attributeName, new Class[0]);
			return method.invoke(annotation);
		}
		catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Retrieve the <em>default value</em> of the {@code &quot;value&quot;} attribute
	 * of a single-element Annotation, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the default value
	 * @return the default value, or {@code null} if not found
	 * @see #getDefaultValue(Annotation, String)
	 */
	public static Object getDefaultValue(Annotation annotation) {
		return getDefaultValue(annotation, VALUE);
	}

	/**
	 * Retrieve the <em>default value</em> of a named Annotation attribute, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the default value
	 * @param attributeName the name of the attribute value to retrieve
	 * @return the default value of the named attribute, or {@code null} if not found
	 * @see #getDefaultValue(Class, String)
	 */
	public static Object getDefaultValue(Annotation annotation, String attributeName) {
		return getDefaultValue(annotation.annotationType(), attributeName);
	}

	/**
	 * Retrieve the <em>default value</em> of the {@code &quot;value&quot;} attribute
	 * of a single-element Annotation, given the {@link Class annotation type}.
	 * @param annotationType the <em>annotation type</em> for which the default value should be retrieved
	 * @return the default value, or {@code null} if not found
	 * @see #getDefaultValue(Class, String)
	 */
	public static Object getDefaultValue(Class<? extends Annotation> annotationType) {
		return getDefaultValue(annotationType, VALUE);
	}

	/**
	 * Retrieve the <em>default value</em> of a named Annotation attribute, given the {@link Class annotation type}.
	 * @param annotationType the <em>annotation type</em> for which the default value should be retrieved
	 * @param attributeName the name of the attribute value to retrieve.
	 * @return the default value of the named attribute, or {@code null} if not found
	 * @see #getDefaultValue(Annotation, String)
	 */
	public static Object getDefaultValue(Class<? extends Annotation> annotationType, String attributeName) {
		try {
			Method method = annotationType.getDeclaredMethod(attributeName, new Class[0]);
			return method.getDefaultValue();
		}
		catch (Exception ex) {
			return null;
		}
	}

}

/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.k8s.templates

fun unionType(vararg unions: String): String = unions.joinToString("|") { "\"$it\"" }
fun optionalUnionType(vararg unions: String): String = """(${unions.joinToString("|") { "\"$it\""}})?"""

/**
 * Types that used to be classes in a module, but got moved to become their own modules.
 *
 * A typealias gets created to point to the new module, with a deprecation.
 */
val movedTypes = listOf(
  Pair("api.core.v1.PodSpec", "SeccompProfile") to "api.core.v1.SeccompProfile",
  Pair("api.core.v1.PodSpec", "WindowsSecurityContextOptions") to "api.core.v1.WindowsSecurityContextOptions",
  Pair("api.core.v1.PodSpec", "EphemeralContainer") to "api.core.v1.EphemeralContainer",
  Pair("api.core.v1.PodSpec", "VolumeDevice") to "api.core.v1.VolumeDevice",
  Pair("api.core.v1.PodSpec", "Probe") to "api.core.v1.Probe",
  Pair("api.core.v1.PodSpec", "TCPSocketAction") to "api.core.v1.TCPSocketAction",
  Pair("api.core.v1.PodSpec", "ExecAction") to "api.core.v1.ExecAction",
  Pair("api.core.v1.PodSpec", "HTTPGetAction") to "api.core.v1.HTTPGetAction",
  Pair("api.core.v1.PodSpec", "HTTPHeader") to "api.core.v1.HTTPHeader",
  Pair("api.core.v1.PodSpec", "SecurityContext") to "api.core.v1.SecurityContext",
  Pair("api.core.v1.PodSpec", "Capabilities") to "api.core.v1.Capabilities",
  Pair("api.core.v1.PodSpec", "ContainerPort") to "api.core.v1.ContainerPort",
  Pair("api.core.v1.PodSpec", "Lifecycle") to "api.core.v1.Lifecycle",
  Pair("api.core.v1.PodSpec", "Handler") to "api.core.v1.Handler",
  Pair("api.resource.v1alpha2.ResourceClaimSpec", "ResourceClaimParametersReference") to "api.resource.v1alpha2.ResourceClaimParametersReference",
  Pair("api.resource.v1alpha2.ResourceClass", "ResourceClassParametersReference") to "api.resource.v1alpha2.ResourceClassParametersReference"
)

/**
 * Names that got renamed, but didn't change modules.
 *
 * A typealias gets created to point to the new name, with a deprecation.
 */
val renamedTypes = mapOf(
  "api.core.v1.Lifecycle" to Pair("Handler", "LifecycleHandler")
)

/**
 * Force these resources to be rendered as Pkl modules instead of inlined classes to help avoid breaking changes.
 *
 * PN: Would be nice if this would work similar to `movedTypes` instead of keeping the modules around forever.
 * Ideally, we would generate the new classes and had a way to turn the old modules into type aliases (when used in type position).
 *
 * Alternatively, we could generate type aliases pointing to the old modules and deprecate the old modules.
 * After a while, we would stop forcing modules, and the type aliases would turn into classes.
 */
val forceModule: MutableSet<String> = mutableSetOf(
  "api.core.v1.Volume",
  "api.core.v1.SeccompProfile",
  "api.core.v1.WindowsSecurityContextOptions",
  "api.core.v1.EphemeralContainer",
  "api.core.v1.VolumeDevice",
  "api.core.v1.Probe",
  "api.core.v1.TCPSocketAction",
  "api.core.v1.ExecAction",
  "api.core.v1.HTTPGetAction",
  "api.core.v1.SecurityContext",
  "api.core.v1.EnvVar",
  "api.core.v1.ContainerPort",
  "api.core.v1.VolumeMount",
  "api.core.v1.Lifecycle",
  "api.core.v1.EnvFromSource"
)

/**
 * Replaced with native Pkl types or type aliases defined in `K8sObject`
 */
val replacedTypes: List<String> = listOf(
  "apimachinery.pkg.util.intstr.IntOrString",
  "apimachinery.pkg.api.resource.Quantity",
  "apimachinery.pkg.apis.meta.v1.Time",
  "apimachinery.pkg.apis.meta.v1.MicroTime",
  "apimachinery.pkg.apis.meta.v1.FieldsV1",
  "apimachinery.pkg.apis.meta.v1.Patch",
  "apimachinery.pkg.runtime.RawExtension",
  "apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrArray",
  "apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrBool",
  "apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrStringArray",
  "apiextensions-apiserver.pkg.apis.apiextensions.v1.JSON",
  "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.JSONSchemaPropsOrArray",
  "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.JSONSchemaPropsOrBool",
  "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.JSONSchemaPropsOrStringArray",
  "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.JSON"
)

val propertyTypeOverrides: MutableMap<Triple<String, String?, String>, String> = run {
  val trueFalseUnknown = unionType("True", "False", "Unknown")
  val objectPodsResource = unionType("ContainerResource", "External", "Object", "Pods", "Resource")
  val neverPreemptyLowerPriorityNullable = optionalUnionType("Never", "PreemptLowerPriority")
  val alwaysNeverIfNotPresentNullable = optionalUnionType("Always", "Never", "IfNotPresent")
  val clusterNamespacedStarNullable = optionalUnionType("Cluster", "Namespaced", "*")
  val portNumber = "PortNumber"
  val portNumberNullable = "PortNumber?"
  val portNameNullable = "PortName?"
  val portNumberOrName = "PortNumber|PortName"
  val portNumberOrNameNullable = "(PortNumber|PortName)?"
  val allowForbidReplaceNullable = optionalUnionType("Allow", "Forbid", "Replace")
  val udpTcpSctpNullable = optionalUnionType("UDP", "TCP", "SCTP")
  val udpTcpSctp = unionType("UDP", "TCP", "SCTP")
  // currently we don't treat empty port name the same as null, even though docs for some ports seem to indicate they are the same
  val hasUniquePortNames = "module.hasUniquePortNames(this)"
  val hasNonNullPortNames = "module.hasNonNullPortNames(this)"
  val rfc1035Label = "Rfc1035Label"
  val rfc1035LabelNullable = "Rfc1035Label?"
  val rfc1123Label = "Rfc1123Label"
  val rfc1123LabelNullable = "Rfc1123Label?"
  val certificateUsages =
    """"signing"|"digital signature"|"content commitment"|"key encipherment"|"key agreement"|"data encipherment"|"cert sign"|"crl sign"|"encipher only"|"decipher only"|"any"|"server auth"|"client auth"|"code signing"|"email protection"|"s/mime"|"ipsec end system"|"ipsec tunnel"|"ipsec user"|"timestamping"|"ocsp signing"|"microsoft sgc"|"netscape sgc""""
  mutableMapOf(
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.CustomResourceDefinition",
      "ServiceReference",
      "port"
    ) to portNumberNullable,
    // docs say that `scope` has a default, hence it must be optional
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.CustomResourceDefinition",
      "CustomResourceDefinitionSpec",
      "scope"
    ) to "(\"Cluster\"|\"Namespaced\")?",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.CustomResourceDefinition",
      "CustomResourceConversion",
      "strategy"
    ) to "\"None\"|\"Webhook\"(webhookClientConfig != null)",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.CustomResourceDefinition",
      "WebhookClientConfig",
      "service"
    ) to "ServiceReference?((this != null).xor(url != null))",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.CustomResourceDefinition",
      "WebhookClientConfig",
      "url"
    ) to "String(matches(Regex(\"https://[^@#?]*\")))?",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.CustomResourceDefinition",
      "JSONSchemaProps",
      "minimum"
    ) to "Number?",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.CustomResourceDefinition",
      "JSONSchemaProps",
      "maximum"
    ) to "Number?",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.CustomResourceDefinition",
      "JSONSchemaProps",
      "multipleOf"
    ) to "Number?",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1.CustomResourceDefinition",
      "ServiceReference",
      "port"
    ) to portNumberNullable,
    // docs say that `scope` has a default, hence it must be optional
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1.CustomResourceDefinition",
      "CustomResourceDefinitionSpec",
      "scope"
    ) to """"Cluster"|"Namespaced"""",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1.CustomResourceDefinition",
      "CustomResourceConversion",
      "strategy"
    ) to """"None"|"Webhook"(webhook != null)""",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1.CustomResourceDefinition",
      "WebhookClientConfig",
      "service"
    ) to "ServiceReference?((this != null).xor(url != null))",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1.CustomResourceDefinition",
      "WebhookClientConfig",
      "url"
    ) to """String(matches(Regex("https://[^@#?]*")))?""",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1.CustomResourceDefinition",
      "JSONSchemaProps",
      "minimum"
    ) to "Number?",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1.CustomResourceDefinition",
      "JSONSchemaProps",
      "maximum"
    ) to "Number?",
    Triple(
      "apiextensions-apiserver.pkg.apis.apiextensions.v1.CustomResourceDefinition",
      "JSONSchemaProps",
      "multipleOf"
    ) to "Number?",
    Triple(
      "apimachinery.pkg.apis.meta.v1.APIResourceList",
      "APIResource",
      "verbs"
    ) to "Listing<ApiRequestVerb>(isDistinct)",
    Triple(
      "apimachinery.pkg.apis.meta.v1.DeleteOptions",
      null,
      "orphanDependents"
    ) to "Boolean?(this == null || propagationPolicy == null)",
    Triple(
      "apimachinery.pkg.apis.meta.v1.DeleteOptions",
      null,
      "propagationPolicy"
    ) to """("Orphan"|"Background"|"Foreground")?""",
    Triple(
      "apimachinery.pkg.apis.meta.v1.LabelSelector",
      "LabelSelectorRequirement",
      "operator"
    ) to """"In"|"NotIn"|"Exists"|"DoesNotExist"""",
    Triple("apimachinery.pkg.apis.meta.v1.ObjectMeta", null, "namespace") to rfc1123LabelNullable,
    Triple("apimachinery.pkg.apis.meta.v1.ObjectMeta", "ManagedFieldsEntry", "operation") to """("Apply"|"Update")?""",
    Triple("apimachinery.pkg.apis.meta.v1.Status", null, "status") to """("Success"|"Failure")?""",
    Triple(
      "api.admissionregistration.v1beta1.MutatingWebhookConfiguration",
      "MutatingWebhook",
      "matchPolicy"
    ) to "(\"Exact\"|\"Equivalent\")?",
    Triple(
      "api.admissionregistration.v1beta1.MutatingWebhookConfiguration",
      "MutatingWebhook",
      "reinvocationPolicy"
    ) to "(\"Never\"|\"IfNeeded\")?",
    Triple(
      "api.admissionregistration.v1beta1.MutatingWebhookConfiguration",
      "MutatingWebhook",
      "timeoutSeconds"
    ) to "Int(isBetween(1, 30))?",
    Triple(
      "api.admissionregistration.v1beta1.MutatingWebhookConfiguration",
      "MutatingWebhook",
      "failurePolicy"
    ) to "(\"Ignore\"|\"Fail\")?",
    Triple(
      "api.admissionregistration.v1beta1.MutatingWebhookConfiguration",
      "MutatingWebhook",
      "sideEffects"
    ) to "(\"Unknown\"|\"None\"|\"Some\"|\"NoneOnDryRun\")?",
    Triple("api.admissionregistration.v1beta1.RuleWithOperations", null, "apiGroups") to """Listing<"*"|String>?""",
    Triple("api.admissionregistration.v1beta1.RuleWithOperations", null, "apiVersions") to """Listing<"*"|String>?""",
    Triple(
      "api.admissionregistration.v1beta1.RuleWithOperations",
      null,
      "operations"
    ) to """Listing<"CREATE"|"UPDATE"|"DELETE"|"CONNECT"|"*">?""",
    Triple("api.admissionregistration.v1beta1.RuleWithOperations", null, "scope") to clusterNamespacedStarNullable,
    Triple(
      "api.admissionregistration.v1beta1.ValidatingWebhookConfiguration",
      "ValidatingWebhook",
      "matchPolicy"
    ) to "(\"Exact\"|\"Equivalent\")?",
    Triple(
      "api.admissionregistration.v1beta1.ValidatingWebhookConfiguration",
      "ValidatingWebhook",
      "timeoutSeconds"
    ) to "Int(isBetween(1, 30))?",
    Triple(
      "api.admissionregistration.v1beta1.ValidatingWebhookConfiguration",
      "ValidatingWebhook",
      "failurePolicy"
    ) to "(\"Ignore\"|\"Fail\")?",
    Triple(
      "api.admissionregistration.v1beta1.ValidatingWebhookConfiguration",
      "ValidatingWebhook",
      "sideEffects"
    ) to "(\"Unknown\"|\"None\"|\"Some\"|\"NoneOnDryRun\")?",
    Triple(
      "api.admissionregistration.v1beta1.WebhookClientConfig",
      null,
      "service"
    ) to "ServiceReference?((this != null).xor(url != null))",
    Triple(
      "api.admissionregistration.v1beta1.WebhookClientConfig",
      null,
      "url"
    ) to "String(matches(Regex(\"https://[^@#?]*\")))?",
    Triple("api.admissionregistration.v1beta1.WebhookClientConfig", "ServiceReference", "port") to portNumberNullable,
    Triple(
      "api.admissionregistration.v1.MutatingWebhookConfiguration",
      "MutatingWebhook",
      "matchPolicy"
    ) to """("Exact"|"Equivalent")?""",
    Triple(
      "api.admissionregistration.v1.MutatingWebhookConfiguration",
      "MutatingWebhook",
      "reinvocationPolicy"
    ) to """("Never"|"IfNeeded")?""",
    Triple(
      "api.admissionregistration.v1.MutatingWebhookConfiguration",
      "MutatingWebhook",
      "timeoutSeconds"
    ) to "Int(isBetween(1, 30))?",
    Triple(
      "api.admissionregistration.v1.MutatingWebhookConfiguration",
      "MutatingWebhook",
      "failurePolicy"
    ) to """("Ignore"|"Fail")?""",
    Triple(
      "api.admissionregistration.v1.MutatingWebhookConfiguration",
      "MutatingWebhook",
      "sideEffects"
    ) to """"None"|"NoneOnDryRun"""",
    Triple("api.admissionregistration.v1.RuleWithOperations", null, "apiGroups") to """Listing<"*"|String>?""",
    Triple("api.admissionregistration.v1.RuleWithOperations", null, "apiVersions") to """Listing<"*"|String>?""",
    Triple(
      "api.admissionregistration.v1.RuleWithOperations",
      null,
      "operations"
    ) to """Listing<"CREATE"|"UPDATE"|"DELETE"|"CONNECT"|"*">?""",
    Triple("api.admissionregistration.v1.RuleWithOperations", null, "scope") to clusterNamespacedStarNullable,
    Triple(
      "api.admissionregistration.v1.ValidatingWebhookConfiguration",
      "ValidatingWebhook",
      "matchPolicy"
    ) to """("Exact"|"Equivalent")?""",
    Triple(
      "api.admissionregistration.v1.ValidatingWebhookConfiguration",
      "ValidatingWebhook",
      "timeoutSeconds"
    ) to "Int(isBetween(1, 30))?",
    Triple(
      "api.admissionregistration.v1.ValidatingWebhookConfiguration",
      "ValidatingWebhook",
      "failurePolicy"
    ) to """("Ignore"|"Fail")?""",
    Triple(
      "api.admissionregistration.v1.ValidatingWebhookConfiguration",
      "ValidatingWebhook",
      "sideEffects"
    ) to """"None"|"NoneOnDryRun"""",
    Triple(
      "api.admissionregistration.v1.WebhookClientConfig",
      null,
      "service"
    ) to "ServiceReference?((this != null).xor(url != null))",
    Triple(
      "api.admissionregistration.v1.WebhookClientConfig",
      null,
      "url"
    ) to """String(matches(Regex("https://[^@#?]*")))?""",
    Triple("api.admissionregistration.v1.WebhookClientConfig", "ServiceReference", "port") to portNumberNullable,

    Triple("api.apps.v1.DaemonSet", "DaemonSetCondition", "status") to trueFalseUnknown,
//    Triple("api.apps.v1.DaemonSet", "DaemonSetUpdateStrategy", "type") to optionalUnionType("OnDelete", "RollingUpdate"),
    Triple("api.apps.v1.ReplicaSet", "ReplicaSetCondition", "status") to trueFalseUnknown,
    Triple("api.apps.v1.Deployment", "DeploymentCondition", "status") to trueFalseUnknown,
    // see: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#pod-template
    Triple(
      "api.apps.v1.Deployment",
      "DeploymentSpec",
      "template"
    ) to """PodTemplateSpec(this.spec?.restartPolicy is "Always"?)""",
    Triple("api.apps.v1.StatefulSet", "StatefulSetCondition", "status") to trueFalseUnknown,

    Triple(
      "api.authorization.v1beta1.SelfSubjectRulesReview",
      "ResourceRule",
      "verbs"
    ) to "Listing<ApiRequestVerb>(isDistinct)",
    Triple(
      "api.authorization.v1beta1.SelfSubjectRulesReview",
      "NonResourceRule",
      "verbs"
    ) to "Listing<HttpRequestVerb>(isDistinct)",
    Triple(
      "api.authorization.v1beta1.SubjectAccessReviewStatus",
      null,
      "denied"
    ) to "Boolean?(allowed.implies(this == false))",
    Triple(
      "api.authorization.v1.SelfSubjectRulesReview",
      "ResourceRule",
      "verbs"
    ) to "Listing<ApiRequestVerb>(isDistinct)",
    Triple(
      "api.authorization.v1.SelfSubjectRulesReview",
      "NonResourceRule",
      "verbs"
    ) to "Listing<HttpRequestVerb>(isDistinct)",
    Triple(
      "api.authorization.v1.SubjectAccessReviewStatus",
      null,
      "denied"
    ) to "Boolean?(allowed.implies(this == false))",

    Triple("api.autoscaling.v2beta1.HorizontalPodAutoscaler", "MetricSpec", "type") to objectPodsResource,
    Triple("api.autoscaling.v2beta1.HorizontalPodAutoscaler", "MetricStatus", "type") to objectPodsResource,
    Triple("api.autoscaling.v2beta2.HorizontalPodAutoscaler", "MetricSpec", "type") to objectPodsResource,
    Triple("api.autoscaling.v2beta2.HorizontalPodAutoscaler", "MetricStatus", "type") to objectPodsResource,
    Triple(
      "api.autoscaling.v2beta2.HorizontalPodAutoscaler",
      "MetricTarget",
      "type"
    ) to """"Utilization"|"Value"|"AverageValue"""",

    Triple(
      "api.autoscaling.v2.HorizontalPodAutoscaler",
      "MetricSpec",
      "type"
    ) to objectPodsResource,
    Triple(
      "api.autoscaling.v2.HorizontalPodAutoscaler",
      "MetricStatus",
      "type"
    ) to objectPodsResource,
    Triple(
      "api.autoscaling.v2.HorizontalPodAutoscaler",
      "HorizontalPodAutoscalerCondition",
      "status"
    ) to trueFalseUnknown,

    Triple("api.batch.v1beta1.CronJob", "CronJobSpec", "concurrencyPolicy") to allowForbidReplaceNullable,
    Triple("api.batch.v2alpha1.CronJob", "CronJobSpec", "concurrencyPolicy") to allowForbidReplaceNullable,
    Triple("api.batch.v1.Job", "JobCondition", "status") to trueFalseUnknown,

    Triple(
      "api.core.v1.ComponentStatus",
      "ComponentCondition",
      "type"
    ) to """"Healthy"|String""",
    Triple(
      "api.core.v1.ComponentStatus",
      "ComponentCondition",
      "status"
    ) to """String((type == "Healthy").implies(this is "True"|"False"|"Unknown"))""",
    Triple("api.core.v1.Endpoints", "EndpointPort", "name") to rfc1035LabelNullable,
    Triple("api.core.v1.Endpoints", "EndpointPort", "protocol") to udpTcpSctpNullable,
    Triple("api.core.v1.Endpoints", "EndpointPort", "port") to portNumber,
    Triple(
      "api.core.v1.Endpoints",
      "EndpointSubset",
      "ports"
    ) to "Listing<EndpointPort>($hasUniquePortNames, $hasNonNullPortNames)?",
    Triple("api.core.v1.Namespace", "NamespaceCondition", "status") to trueFalseUnknown,
    Triple(
      "api.core.v1.FlockerVolumeSource",
      null,
      "datasetName"
    ) to "String?((this != null).xor(datasetUUID != null))",
    Triple(
      "api.core.v1.Pod",
      "ContainerState",
      "running"
    ) to "ContainerStateRunning?(module.exactlyOneSet(this, waiting, terminated))",
    Triple("api.core.v1.Pod", "ContainerStatus", "name") to rfc1035Label,
    Triple(
      "api.core.v1.PodSpec",
      null,
      "dnsPolicy"
    ) to """("ClusterFirstWithHostNet"|"ClusterFirst"|"Default"|"None")?""",
    Triple("api.core.v1.PodSpec", null, "hostAliases") to "Listing<HostAlias>(hostNetwork != true)?",
    Triple("api.core.v1.PodSpec", null, "preemptionPolicy") to neverPreemptyLowerPriorityNullable,
    Triple("api.core.v1.PodSpec", null, "restartPolicy") to """("Always"|"OnFailure"|"Never")?""",
    Triple("api.core.v1.PodSpec", "Container", "imagePullPolicy") to alwaysNeverIfNotPresentNullable,
    Triple("api.core.v1.PodSpec", "Container", "name") to rfc1035Label,
    Triple("api.core.v1.PodSpec", "Container", "ports") to "Listing<ContainerPort>($hasUniquePortNames)?",
    Triple("api.core.v1.PodSpec", "PodSecurityContext", "fsGroupChangePolicy") to """("OnRootMismatch"|"Always")?""",
    Triple("api.core.v1.ContainerPort", null, "name") to portNameNullable,
    Triple("api.core.v1.ContainerPort", null, "protocol") to udpTcpSctpNullable,
    Triple("api.core.v1.ContainerPort", null, "containerPort") to portNumber,
    Triple("api.core.v1.ContainerPort", null, "hostPort") to portNumberNullable,
    Triple("api.core.v1.EphemeralContainer", null, "imagePullPolicy") to alwaysNeverIfNotPresentNullable,
    Triple("api.core.v1.EphemeralContainer", null, "name") to rfc1035Label,
    Triple("api.core.v1.Probe", null, "exec") to "ExecAction?(module.exactlyOneSet(this, httpGet, tcpSocket))",
    Triple(
      "api.core.v1.Lifecycle",
      "LifecycleHandler",
      "exec"
    ) to "ExecAction?(module.exactlyOneSet(this, httpGet, tcpSocket))",
    Triple("api.core.v1.HTTPGetAction", null, "port") to portNumberOrName,
    Triple("api.core.v1.PodSpec", "PodAffinityTerm", "topologyKey") to "String(!isEmpty)",
    Triple("api.core.v1.SeccompProfile", null, "localhostProfile") to """String(type is "Localhost")?""",
    Triple("api.core.v1.SeccompProfile", null, "type") to """"Localhost"|"RuntimeDefault"|"Unconfined"""",
    Triple("api.core.v1.TCPSocketAction", null, "port") to portNumberOrName,
    Triple("api.core.v1.PodSpec", "TopologySpreadConstraint", "maxSkew") to "Int32(this >= 1)",
    Triple("api.core.v1.Node", "DaemonEndpoint", "Port") to portNumber,
    Triple("api.core.v1.Node", "NodeAddress", "type") to unionType(
      "ExternalDNS",
      "ExternalIP",
      "Hostname",
      "InternalDNS",
      "InternalIP",
    ),
    Triple("api.core.v1.Node", "NodeCondition", "status") to trueFalseUnknown,
    Triple("api.core.v1.Node", "NodeCondition", "type") to unionType(
      "DiskPressure",
      "MemoryPressure",
      "NetworkUnavailable",
      "PIDPressure",
      "Ready"
    ),
    Triple("api.core.v1.Node", "Taint", "effect") to unionType("NoSchedule", "PreferNoSchedule", "NoExecute"),
    Triple("api.core.v1.Node", "Taint", "value") to "String",
    Triple(
      "api.core.v1.PersistentVolumeSpec",
      null,
      "persistentVolumeReclaimPolicy"
    ) to """("Retain"|"Delete"|"Recycle")?""",
    Triple(
      "api.core.v1.PersistentVolumeClaim",
      "PersistentVolumeClaimCondition",
      "type"
    ) to unionType("FileSystemResizePending", "Resizing"),
    Triple("api.core.v1.ReplicationController", "ReplicationControllerCondition", "status") to trueFalseUnknown,
    Triple(
      "api.core.v1.ResourceQuota",
      "ScopedResourceSelectorRequirement",
      "operator"
    ) to unionType("In", "NotIn", "Exists", "DoesNotExist"),
    Triple("api.core.v1.ResourceQuota", "ScopedResourceSelectorRequirement", "scopeName") to unionType(
      "BestEffort",
      "CrossNamespacePodAffinity",
      "NotBestEffort",
      "NotTerminating",
      "PriorityClass",
      "Terminating"
    ),
    Triple("api.core.v1.Service", "ServicePort", "protocol") to udpTcpSctpNullable,
    Triple("api.core.v1.Service", "ServicePort", "port") to portNumber,
    Triple("api.core.v1.Service", "ServicePort", "name") to rfc1035LabelNullable,
    Triple("api.core.v1.Service", "ServicePort", "nodePort") to portNumberNullable,
    Triple("api.core.v1.Service", "ServicePort", "targetPort") to portNumberOrNameNullable,
    Triple("api.core.v1.Service", null, "spec") to "ServiceSpec",
    Triple("api.core.v1.Service", "ServiceSpec", "clusterIP") to """("None"|""|String)?""",
    Triple("api.core.v1.Service", "ServiceSpec", "externalName") to """Rfc1123Hostname(type == "ExternalName")?""",
    Triple("api.core.v1.Service", "ServiceSpec", "healthCheckNodePort") to portNumberNullable,
    Triple("api.core.v1.Service", "ServiceSpec", "loadBalancerIP") to """String(type == "LoadBalancer")?""",
    Triple(
      "api.core.v1.Service",
      "ServiceSpec",
      "ports"
    ) to "Listing<ServicePort>($hasUniquePortNames, $hasNonNullPortNames, !isEmpty)?",
    Triple(
      "api.core.v1.Service",
      "ServiceSpec",
      "type"
    ) to """("ExternalName"|"ClusterIP"|"NodePort"|"LoadBalancer")?""",
    Triple("api.core.v1.Toleration", null, "effect") to """("NoSchedule"|"PreferNoSchedule"|"NoExecute")?""",
    Triple("api.core.v1.Toleration", null, "operator") to """("Exists"|"Equal")?""",
    Triple("api.core.v1.Toleration", null, "value") to """String((operator == "Exists").implies(isEmpty))?""",
    Triple("api.core.v1.Toleration", null, "key") to """String(isEmpty.implies(operator == "Exists"))?""",
    Triple("api.core.v1.Volume", null, "name") to rfc1035Label,
    Triple("api.core.v1.Volume", "SecretVolumeSource", "defaultMode") to "Int(isBetween(0, 511))?",
    Triple("api.core.v1.Volume", "KeyToPath", "mode") to "Int(isBetween(0, 511))?",
    Triple("api.core.v1.Volume", "ProjectedVolumeSource", "defaultMode") to "Int(isBetween(0, 511))?",
    Triple("api.core.v1.Volume", "DownwardAPIVolumeFile", "mode") to "Int(isBetween(0, 511))?",
    Triple("api.core.v1.Volume", "DownwardAPIVolumeSource", "defaultMode") to "Int(isBetween(0, 511))?",
    Triple("api.core.v1.Volume", "ConfigMapVolumeSource", "defaultMode") to "Int(isBetween(0, 511))?",

    Triple(
      "api.certificates.v1beta1.CertificateSigningRequest",
      "CertificateSigningRequestSpec",
      "usages"
    ) to certificateUsages,
    Triple(
      "api.certificates.v1beta1.CertificateSigningRequest",
      "CertificateSigningRequestCondition",
      "type"
    ) to """"Approved"|"Denied"|"Failed"|String""",
    Triple(
      "api.certificates.v1beta1.CertificateSigningRequest",
      "CertificateSigningRequestCondition",
      "status"
    ) to """"True"|"False"|"Unknown"""",
    Triple(
      "api.certificates.v1.CertificateSigningRequest",
      "CertificateSigningRequestSpec",
      "usages"
    ) to certificateUsages,
    Triple(
      "api.certificates.v1.CertificateSigningRequest",
      "CertificateSigningRequestCondition",
      "type"
    ) to """"Approved"|"Denied"|"Failed"|String""",
    Triple(
      "api.certificates.v1.CertificateSigningRequest",
      "CertificateSigningRequestCondition",
      "status"
    ) to """"True"|"False"|"Unknown"""",
    Triple("api.discovery.v1beta1.EndpointSlice", null, "endpoints") to "Listing<Endpoint>(length <= 1000)",
    Triple(
      "api.discovery.v1beta1.EndpointSlice",
      null,
      "ports"
    ) to "Listing<EndpointPort>(length <= 100, $hasUniquePortNames)?",
    Triple(
      "api.discovery.v1beta1.EndpointSlice",
      "Endpoint",
      "addresses"
    ) to "Listing<String>(length.isBetween(1, 100))",
    Triple("api.discovery.v1beta1.EndpointSlice", "Endpoint", "hostname") to rfc1123LabelNullable,
    Triple(
      "api.discovery.v1beta1.EndpointSlice",
      "Endpoint",
      "topology"
    ) to """Mapping<"kubernetes.io/hostname"|"topology.kubernetes.io/zone"|"topology.kubernetes.io/region"|String, String>?""",
    Triple("api.discovery.v1beta1.EndpointSlice", "EndpointPort", "name") to portNameNullable,
    Triple("api.discovery.v1beta1.EndpointSlice", "EndpointPort", "protocol") to udpTcpSctpNullable,
    Triple("api.discovery.v1beta1.EndpointSlice", "EndpointPort", "port") to portNumberNullable,

    Triple(
      "api.extensions.v1beta1.Ingress",
      "HTTPIngressPath",
      "pathType"
    ) to """("Exact"|"Prefix"|"ImplementationSpecific")?""",
    Triple("api.extensions.v1beta1.Ingress", "IngressBackend", "servicePort") to portNumberOrName,
    Triple(
      "api.networking.v1.Ingress",
      "HTTPIngressPath",
      "path"
    ) to """String(startsWith("/"))?""",
    Triple(
      "api.networking.v1.Ingress",
      "HTTPIngressPath",
      "pathType"
    ) to """"Exact"(path != null)|"Prefix"(path != null)|"ImplementationSpecific"""",
    Triple("api.networking.v1.Ingress", "IngressServiceBackend", "port") to "ServiceBackendPort", // not optional
    Triple(
      "api.networking.v1.Ingress",
      "ServiceBackendPort",
      "name"
    ) to "$portNameNullable((this != null).xor(number != null))",
    Triple("api.networking.v1.Ingress", "ServiceBackendPort", "number") to portNumberNullable,
    Triple(
      "api.networking.v1.NetworkPolicy",
      "NetworkPolicyPeer",
      "ipBlock"
    ) to "IPBlock(podSelector == null, namespaceSelector == null)?",
    Triple(
      "api.networking.v1.NetworkPolicy",
      "NetworkPolicySpec",
      "policyTypes"
    ) to """Listing<"Ingress"|"Egress">(isDistinct)?""",
    Triple("api.networking.v1.NetworkPolicy", "NetworkPolicyPort", "protocol") to udpTcpSctpNullable,
    Triple("api.networking.v1.NetworkPolicy", "NetworkPolicyPort", "port") to portNumberOrNameNullable,

    Triple("api.node.v1alpha1.RuntimeClass", "RuntimeClassSpec", "runtimeHandler") to rfc1123Label,
    Triple("api.node.v1beta1.RuntimeClass", null, "handler") to rfc1123Label,

    Triple("api.rbac.v1alpha1.PolicyRule", null, "verbs") to "Listing<ApiRequestVerb>(isDistinct)",
    Triple("api.rbac.v1beta1.PolicyRule", null, "verbs") to "Listing<ApiRequestVerb>(isDistinct)",
    Triple("api.rbac.v1.PolicyRule", null, "verbs") to "Listing<ApiRequestVerb>(isDistinct)",

    Triple("api.scheduling.v1alpha1.PriorityClass", null, "preemptionPolicy") to neverPreemptyLowerPriorityNullable,
    Triple("api.scheduling.v1beta1.PriorityClass", null, "preemptionPolicy") to neverPreemptyLowerPriorityNullable,
    Triple("api.scheduling.v1.PriorityClass", null, "preemptionPolicy") to neverPreemptyLowerPriorityNullable,

    Triple(
      "kube-aggregator.pkg.apis.apiregistration.v1beta1.APIService",
      "ServiceReference",
      "port"
    ) to portNumberNullable,
    Triple("kube-aggregator.pkg.apis.apiregistration.v1.APIService", "ServiceReference", "port") to portNumberNullable,
    Triple("api.batch.v1.JobSpec", null, "completionMode") to """("NonIndexed"|"Indexed")?""",
    Triple("api.core.v1.LoadBalancerStatus", "PortStatus", "protocol") to udpTcpSctp,
    Triple(
      "api.networking.v1beta1.IngressClass",
      "IngressClassParametersReference",
      "scope"
    ) to "(\"Cluster\"|\"Namespace\")?",
    Triple(
      "api.networking.v1beta1.IngressClass",
      "IngressClassParametersReference",
      "namespace"
    ) to "String?(module.onlyAllowedIf(this, scope == \"Namespace\"))",
    Triple(
      "api.networking.v1.IngressClass",
      "IngressClassParametersReference",
      "scope"
    ) to """("Cluster"|"Namespace")?""",
    Triple(
      "api.networking.v1.IngressClass",
      "IngressClassParametersReference",
      "namespace"
    ) to """String?(module.onlyAllowedIf(this, scope == "Namespace"))""",
    Triple(
      "api.authentication.v1.TokenRequest",
      "BoundObjectReference",
      "kind"
    ) to optionalUnionType("Pod", "Secret"),
    Triple(
      "api.flowcontrol.v1beta2.PriorityLevelConfiguration",
      "PriorityLevelConfigurationSpec",
      "type"
    ) to unionType("Exempt", "Limited"),
    Triple(
      "api.flowcontrol.v1beta2.PriorityLevelConfiguration",
      "LimitResponse",
      "type"
    ) to unionType("Queue", "Reject"),
    Triple(
      "api.flowcontrol.v1beta2.PriorityLevelConfiguration",
      "PriorityLevelConfigurationCondition",
      "status"
    ) to trueFalseUnknown,

  )
}

#!/bin/bash

############################################################################
# Author: EPRGGGZ Gustavo Garcia G.                                        #
#                                                                          #
# Script to collect logfiles for Kubernetes Cluster based on Spider input  #
# The script wil also collect HELM charts configuration                    #
# To use, execute collect_ADP_logs.sh <namespace>                          #
#                                                                          #
############################################################################

############################################################################
#                          History                                         #
#                                                                          #
# 2019-01-25  EPRGGGZ     Fixed bug with events                            #
#                         Added PV                                         #
#                         Added cmm_logs for CM Mediator                   #
#                                                                          #
#                                                                          #
#                                                                          #
# 2019-01-23   Keith Liu   fix bug when get logs of pod which may have more#
#                          more than one container                         #
#                          add more resources for describe logs            #
#                          add timestamp in the log folder name and some   #
#                          improvement                                     #
#                                                                          #
############################################################################

#Fail if empty argument received
if [[ "$#" != "1" ]]; then
    echo "Wrong number of arguments"
    echo "Usage collect_ADP_logs.sh <Kubernetes_namespace>"
    echo "ex:"
    echo "$0 default    #--- to gather the logs for namespace 'default'"
    exit 1
fi


namespace=$1
#Create a directory for placing the logs
log_base_dir=logs_${namespace}_$(date "+%Y-%m-%d-%H-%M-%S")
log_base_path=$PWD/${log_base_dir}
mkdir ${log_base_dir}

get_describe_info() {
    #echo "---------------------------------------"
    echo "-Getting logs for describe info-"
    #echo "---------------------------------------"
    #echo "---------------------------------------"

    des_dir=${log_base_path}/describe
    mkdir ${des_dir}
    for attr in statefulsets deployments services replicasets endpoints daemonsets persistentvolumeclaims configmap pods nodes jobs persistentvolumes rolebindings roles secrets serviceaccounts storageclasses ingresses
        do
            dir=`echo $attr | tr '[:lower:]' '[:upper:]'`
            mkdir ${des_dir}/$dir
            ${KUBECTL_CMD} --namespace ${namespace} get $attr > ${des_dir}/$dir/$attr.txt
            echo "Getting describe information on $dir.."
            for i in `${KUBECTL_CMD} --namespace ${namespace} get $attr | grep -v NAME | awk '{print $1}'`
                do
                    ${KUBECTL_CMD} --namespace ${namespace}  describe  $attr  $i > ${des_dir}/$dir/$i.txt
                done
        done
}
get_events() {
    echo "-Getting list of events -"
    events_dir=${log_base_path}/events
    mkdir ${events_dir}
    ${KUBECTL_CMD} --namespace ${namespace} get events > ${events_dir}/events.txt

}
get_pods_logs() {
    #echo "---------------------------------------"
    echo "-Getting logs per POD-"
    #echo "---------------------------------------"
    #echo "---------------------------------------"

    logs_dir=${log_base_path}/logs
    mkdir ${logs_dir}
    failed_pods_dir=${logs_dir}/failed_pods
    mkdir ${failed_pods_dir}
    ${KUBECTL_CMD} --namespace ${namespace} get pods > ${logs_dir}/All_${namespace}_podstolog.txt
    for i in `${KUBECTL_CMD} --namespace ${namespace} get pods | grep -v NAME | awk '{print $1}'`
        do
            for j in `${KUBECTL_CMD} --namespace ${namespace} get pod $i -o jsonpath='{.spec.containers[*].name}'`
                do
                    ${KUBECTL_CMD} --namespace ${namespace} logs $i -c $j > ${logs_dir}/${i}_${j}.txt
                    ${KUBECTL_CMD} --namespace ${namespace} logs $i -c $j --previous > /dev/null 2>&1 && ${KUBECTL_CMD} --namespace ${namespace} logs $i -c $j --previous > ${failed_pods_dir}/${i}_${j}_previous.txt
                done
        done
}

get_spark_logs() {
    #echo "---------------------------------------"
    echo "-Getting spark application logs-"
    #echo "---------------------------------------"
    #echo "---------------------------------------"
    logs_dir=${log_base_path}/spark_application_logs
    mkdir ${logs_dir}
    for i in `${KUBECTL_CMD} --namespace ${namespace} get pods | grep spark | awk '{print $1}'`
        do
            ${KUBECTL_CMD} cp ${namespace}/$i:/opt/spark-2.4.3-bin-hadoop2.7/work-dir $logs_dir/$i
        done
    sudo chmod 777 ${logs_dir}
    sudo find ${logs_dir} -name "*.jar" -type f -delete
}


get_helm_info() {
    #echo "-----------------------------------------"
    echo "-Getting Helm Charts for the deployments-"
    #echo "-----------------------------------------"
    #echo "-----------------------------------------"

    helm_dir=${log_base_path}/helm
    mkdir ${helm_dir}

    ${HELM_CMD} --namespace ${namespace} list > ${helm_dir}/helm_deployments.txt

    for i in `${HELM_CMD} --namespace ${namespace} list| grep -v NAME | awk '{print $1}'`
        do
            #echo $i
            ${HELM_CMD} get $i > ${helm_dir}/$i.txt
        done
}

compress_files() {
    echo "Generating tar file and removing logs directory..."
    tar cvfz $PWD/${log_base_dir}.tgz ${log_base_dir}
    echo  -e "\e[1m\e[31mGenerated file $PWD/${log_base_dir}.tgz, Please collect logs and attach to any bug raised!\e[0m"
    sudo rm -r $PWD/${log_base_dir}
}

get_describe_info
get_events
get_pods_logs
get_spark_logs || echo "Failed to retrieve spark_application_logs"
get_helm_info
compress_files



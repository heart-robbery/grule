<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <span class="h-panel-title">数据集</span>
            <!--            <span v-color:gray v-font="13">说明~~</span>-->
            &nbsp;&nbsp;
            <h-button @click="showAddPop"><i class="h-icon-plus"></i></h-button>
            <div class="h-panel-right">
                <h-search placeholder="查询" v-width="200" v-model="kw" show-search-button search-text="搜索" @search="load"></h-search>
                <!--                <i class="h-split"></i>-->
                <!--                <button class="h-btn h-btn-green h-btn-m" @click="load">查询</button>-->
            </div>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading">
                <!--                <h-tableitem title="ID" prop="id" align="center"></h-tableitem>-->
                <h-tableitem title="英文名" prop="enName" align="center"></h-tableitem>
                <h-tableitem title="中文名" prop="cnName" align="center"></h-tableitem>
                <h-tableitem title="类型" prop="type" align="center"></h-tableitem>
                <!--                    <h-tableitem title="创建时间" prop="createTime" align="center"></h-tableitem>-->
                <h-tableitem title="描述" prop="comment" align="center"></h-tableitem>
                <h-tableitem title="值函数名" prop="dataCollector" align="center"></h-tableitem>
                <h-tableitem title="操作" align="center" :width="100">
                    <template slot-scope="{data}">
                        <span class="text-hover" @click="showUpdatePop(data)">编辑</span>
                        <span class="text-hover" @click="del(data)">删除</span>
                    </template>
                </h-tableitem>
                <div slot="empty">暂时无数据</div>
            </h-table>
        </div>
        <div v-if="totalRow" class="h-panel-bar">
            <h-pagination ref="pagination" :cur="page" :total="totalRow" :size="pageSize"
                          align="right" @change="load" layout="pager,total"></h-pagination>
        </div>
    </div>
</template>
<script>
    module.exports = {
        data() {
            return {
                loading: false,
                page: 1,
                list: []
            }
        }
    }
</script>
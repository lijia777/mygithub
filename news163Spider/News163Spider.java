package com.itheima.spider.news163Spider;

import com.google.gson.Gson;
import com.itheima.spider.dao.NewsDao;
import com.itheima.spider.pojo.News;
import com.itheima.spider.utils.HttpClientUtils;
import com.itheima.spider.utils.IdWorker;
import com.itheima.spider.utils.JedisUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import redis.clients.jedis.Jedis;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class News163Spider {
   private static IdWorker idWorker  = new IdWorker(0,1);
    private static  NewsDao newsDao = new NewsDao();
    public static void main(String[] args) throws Exception {

        //2. 发送请求, 获取数据
        // 此处获取的json的数据, 并不是一个非标准的json
        //String jsonStr = HttpClientUtils.doGet(indexUrl);
        //jsonStr = splitJson(jsonStr);
        // System.out.println(jsonStr);

        //3. 解析数据(json) :
        /**
         * 1) json格式一共有几种:     二种  一般见复合格式认为是前二种的扩展格式
         *      一种:  [value1,value2,value3 ....]    ---数组格式
         *      二种:  {key1:value1,key2:value2}
         *      三种:  {key1:[value1,value2]}
         *      四种:  [{},{}]
         *
         * 2) 如何区分一个json的格式是一个对象呢, 还是一个数组呢?
         *      查看最外层的符号即可, 如果是[] 那就是数组, 如果{}那就是对象
         *          [{key,[{key,value}]}] : 转回对应的类型
         *              List<Map<String,List<Map<String,String>>>>
         *
         *  3) json格式本质上就是一个字符串: 在js  和 java中表示的类型有所不同的:
         *           js                      java
         *    []    数组                    数组/List/set
         *    {}    对象                    javaBean对象/Map
         *
         *    js中如何定义一个对象:  var persion = {username:'张三'};   persion.username
         */
        //parseJson(jsonStr);

        //1. 确定首页url:
        //String indexUrl = "https://ent.163.com/special/000380VU/newsdata_index.js?callback=data_callback";
        List<String> urlList = new ArrayList<String>();
        urlList.add("https://ent.163.com/special/000380VU/newsdata_index.js?callback=data_callback");
        urlList.add("https://ent.163.com/special/000380VU/newsdata_star.js?callback=data_callback");
        urlList.add("https://ent.163.com/special/000380VU/newsdata_movie.js?callback=data_callback");
        urlList.add("https://ent.163.com/special/000380VU/newsdata_tv.js?callback=data_callback");
        urlList.add("https://ent.163.com/special/000380VU/newsdata_show.js?callback=data_callback");
        urlList.add("https://ent.163.com/special/000380VU/newsdata_music.js?callback=data_callback");

        //5. 分页获取数据
        while(!urlList.isEmpty()) {
            String indexUrl = urlList.remove(0);
            System.out.println("获取了下一个栏目的数据#######################################" );
            page(indexUrl);
        }
    }
    // 执行分页的方法

    public static void  page(String indexUrl) throws  Exception{
        String page = "02";
        while(true) {
            //1. 发送请求获取数据
            // 此处获取的json的数据, 并不是一个非标准的json

            String jsonStr = HttpClientUtils.doGet(indexUrl);
            if(jsonStr==null){
                System.out.println("数据获取完成");
                break;
            }

            jsonStr = splitJson(jsonStr);

            //2. 解析数据, 3 保存数据
            parseJson(jsonStr);

            //4. 获取下一页的url
            if(indexUrl.contains("newsdata_index")){
                indexUrl = "https://ent.163.com/special/000380VU/newsdata_index_" + page + ".js?callback=data_callback";
            }
            if(indexUrl.contains("newsdata_star")){
                indexUrl = "https://ent.163.com/special/000380VU/newsdata_star_" + page + ".js?callback=data_callback";
            }
            if(indexUrl.contains("newsdata_movie")){
                indexUrl = "https://ent.163.com/special/000380VU/newsdata_movie_" + page + ".js?callback=data_callback";
            }
            if(indexUrl.contains("newsdata_tv")){
                indexUrl = "https://ent.163.com/special/000380VU/newsdata_tv_" + page + ".js?callback=data_callback";
            }
            if(indexUrl.contains("newsdata_show")){
                indexUrl = "https://ent.163.com/special/000380VU/newsdata_show_" + page + ".js?callback=data_callback";
            }
            if(indexUrl.contains("newsdata_music")){
                indexUrl = "https://ent.163.com/special/000380VU/newsdata_music_" + page + ".js?callback=data_callback";
            }


            System.out.println(indexUrl);

            //5. page ++
            int pageNum = Integer.parseInt(page);
            pageNum++;

            if(pageNum <10){
                page = "0"+pageNum;
            }else{
                page = pageNum+"";
            }
        }

    }

    // 解析json的方法
    private static void parseJson(String jsonStr)  throws  Exception{
        //3.1 将json字符串转换成 指定的对象
        Gson gson = new Gson();

        List<Map<String, Object>> newsList = gson.fromJson(jsonStr, List.class);
        // 3.2 遍历整个新闻的结合, 获取每一个新闻的对象
        for (Map<String, Object> newsObj : newsList) {
            // 新闻 :  标题, 时间,来源 , 内容 , 新闻编辑  ,  新闻的url
            //3.2.1 获取新闻的url , 需要根据url, 获取详情页中新闻数据
            String docUrl = (String) newsObj.get("docurl");
            // 过滤掉一些不是新闻数据的url
            if(docUrl.contains("photoview")){
                continue;
            }
            if(docUrl.contains("v.163.com")){
                continue;
            }
            if(docUrl.contains("c.m.163.com")){
                continue;
            }
            if(docUrl.contains("dy.163.com")){
                continue;
            }
            // ###################去重处理代码######################
            Jedis jedis = JedisUtils.getJedis();
            Boolean flag = jedis.sismember("bigData:spider:163spider:docurl", docUrl);
            jedis.close();//一定一定一定不要忘记关闭, 否则用着用着没了, 导致程序卡死不动
            if(flag){
                // 代表存在, 表示已经爬取过了
                continue;
            }
            // ###################去重处理代码######################
            //System.out.println(docUrl);
            //3.3 获取新闻详情页的数据
            News news = parseNewsItem(docUrl);

            // 4. 保存数据  ---- 一会  会有问题的

            //System.out.println(news);
            newsDao.saveNews(news);

            // ###################去重处理代码######################
            // 将保存到数据库中的docurl添加到redis的set集合中
            jedis = JedisUtils.getJedis();
            jedis.sadd("bigData:spider:163spider:docurl",news.getDocurl());
            jedis.close();
            // ###################去重处理代码######################

        }
    }
    // 根据url 解析新闻详情页:
    private static News parseNewsItem(String docUrl) throws  Exception {
        System.out.println(docUrl);
        //  3.3.1 发送请求, 获取新闻详情页数据
        String html = HttpClientUtils.doGet(docUrl);

        //3.3.2 解析新闻详情页:
        Document document = Jsoup.parse(html);

        //3.3.2.1 :  解析新闻的标题:
        News news = new News();
        Elements h1El = document.select("#epContentLeft h1");
        String title = h1El.text();
        news.setTitle(title);

        //3.3.2.2 :  解析新闻的时间:
        Elements timeAndSourceEl = document.select(".post_time_source");

        String timeAndSource = timeAndSourceEl.text();

        String[] split = timeAndSource.split("　来源: ");// 请各位一定一定一定要复制, 否则会切割失败
        news.setTime(split[0]);
        //3.3.2.3 :  解析新闻的来源:
        news.setSource(split[1]);
        //3.3.2.4 :  解析新闻的正文:
        Elements ps = document.select("#endText p");
        String content = ps.text();
        news.setContent(content);
        //3.3.2.5 :  解析新闻的编辑:
        Elements spanEl = document.select(".ep-editor");
        // 责任编辑：陈少杰_b6952
        String editor = spanEl.text();
        // 一定要接收返回值, 否则白写了
        editor = editor.substring(editor.indexOf("：")+1,editor.lastIndexOf("_"));
        news.setEditor(editor);
        //3.3.2.6 :  解析新闻的url:
        news.setDocurl(docUrl);
        //3.3.2.7: id
        long id = idWorker.nextId();
        news.setId(id+"");

       return news;
    }

    // 将非标准的json转换为标准的json字符串
    private static String splitJson(String jsonStr) {
        int firstIndex = jsonStr.indexOf("(");
        int lastIndex = jsonStr.lastIndexOf(")");

        return jsonStr.substring(firstIndex + 1, lastIndex);

    }
}

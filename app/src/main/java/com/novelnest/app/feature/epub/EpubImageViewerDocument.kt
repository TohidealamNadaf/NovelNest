package com.novelnest.app.feature.epub

/** Trusted, static document. Publication HTML/scripts never enter this WebView. */
internal object EpubImageViewerDocument {
    // Android's reserved in-app origin. These exact routes are always served from memory.
    const val ORIGIN = "https://appassets.androidplatform.net/epub-image/"
    const val DOCUMENT_URL = ORIGIN + "viewer.html"
    const val IMAGE_URL = ORIGIN + "current"

    // Sanitized SVG wrappers embed at most 6 MiB of validated rasters as base64.
    // Let WebView decode these subresources, but never allow a data: main document.
    private val embeddedRaster = Regex("^data:image/(?:jpeg|png|webp|gif|bmp);base64,", RegexOption.IGNORE_CASE)
    fun isEmbeddedRaster(url: String): Boolean =
        url.length <= 8 * 1024 * 1024 + 128 && embeddedRaster.containsMatchIn(url)

    val html = """
        <!doctype html><html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'">
        <style>
        html,body{margin:0;width:100%;height:100%;overflow:hidden;overscroll-behavior:none;touch-action:none;background:transparent}
        #image{position:absolute;left:0;top:0;transform-origin:0 0;user-select:none;-webkit-user-drag:none;max-width:none;max-height:none}
        </style></head><body><img id="image" alt="EPUB illustration" draggable="false" src="current">
        <script>
        (function(){
            'use strict';
            const image=document.getElementById('image');
            let iw=1,ih=1,w=1,h=1,scale=1,base=1,x=0,y=0,fit='screen',loaded=false,frame=0;
            let points=new Map(),start=null,moved=false,multi=false,lastTap=0,lastTapPoint=null,tapTimer=0;
            function bound(){
                const sw=iw*scale,sh=ih*scale;
                x=sw<=w?(w-sw)/2:Math.max(w-sw,Math.min(0,x));
                y=sh<=h?(h-sh)/2:Math.max(h-sh,Math.min(0,y));
            }
            function draw(){
                bound();
                if(!frame) frame=requestAnimationFrame(function(){
                    frame=0;image.style.transform='translate('+x+'px,'+y+'px) scale('+scale+')';
                });
            }
            function layout(resetZoom){
                if(!loaded)return;
                const ratio=scale/base,cx=(w/2-x)/scale,cy=(h/2-y)/scale;
                w=window.innerWidth;h=window.innerHeight;
                base=fit==='width'?w/iw:fit==='height'?h/ih:fit==='original'?1/window.devicePixelRatio:Math.min(w/iw,h/ih);
                scale=base*(resetZoom?1:ratio);
                x=resetZoom?(w-iw*scale)/2:w/2-cx*scale;
                y=resetZoom?(h-ih*scale)/2:h/2-cy*scale;draw();
            }
            function reset(){layout(true);}
            function zoomAt(value,cx,cy){
                const next=Math.max(Math.min(base,1/window.devicePixelRatio)*0.25,Math.min(Math.max(base*8,4/window.devicePixelRatio),value));
                x=cx-(cx-x)*next/scale;y=cy-(cy-y)*next/scale;scale=next;draw();
            }
            window.nnViewer={
                configure:function(mode,color){
                    document.body.style.backgroundColor=color;
                    if(fit!==mode){fit=mode;reset();}
                },
                zoom:function(factor){zoomAt(scale*factor,w/2,h/2);},
                reset:reset
            };
            image.onload=function(){
                iw=image.naturalWidth||1;ih=image.naturalHeight||1;
                image.style.width=iw+'px';image.style.height=ih+'px';loaded=true;reset();
                window.ImageViewerEvents.ready();
            };
            image.onerror=function(){window.ImageViewerEvents.failed();};
            if(image.complete){ if(image.naturalWidth)image.onload();else image.onerror(); }
            window.addEventListener('resize',function(){layout(false);});
            function snapshot(){
                const p=Array.from(points.values());
                start={x:x,y:y,scale:scale,points:p,
                    distance:p.length>1?Math.hypot(p[1].x-p[0].x,p[1].y-p[0].y):0};
            }
            document.addEventListener('pointerdown',function(e){
                if(e.pointerType==='mouse' && e.button!==0)return;
                e.preventDefault();clearTimeout(tapTimer);
                document.body.setPointerCapture(e.pointerId);
                points.set(e.pointerId,{x:e.clientX,y:e.clientY});
                if(points.size===1){moved=false;multi=false;}
                else {multi=true;moved=true;clearTimeout(tapTimer);lastTap=0;}
                snapshot();
            });
            document.addEventListener('pointermove',function(e){
                if(!points.has(e.pointerId)||!loaded)return;
                e.preventDefault();points.set(e.pointerId,{x:e.clientX,y:e.clientY});
                const p=Array.from(points.values());
                if(p.length>1 && start.points.length>1){
                    const cx=(p[0].x+p[1].x)/2,cy=(p[0].y+p[1].y)/2;
                    const ox=(start.points[0].x+start.points[1].x)/2,oy=(start.points[0].y+start.points[1].y)/2;
                    x=start.x;y=start.y;scale=start.scale;
                    zoomAt(start.scale*Math.hypot(p[1].x-p[0].x,p[1].y-p[0].y)/Math.max(1,start.distance),ox,oy);
                    x+=cx-ox;y+=cy-oy;draw();
                }else if(p.length===1 && start.points.length===1){
                    const dx=p[0].x-start.points[0].x,dy=p[0].y-start.points[0].y;
                    if(Math.hypot(dx,dy)>7)moved=true;
                    x=start.x+dx;y=start.y+dy;draw();
                }
            });
            function up(e){
                if(!points.has(e.pointerId))return;
                points.delete(e.pointerId);
                if(e.type==='pointercancel'){moved=true;lastTap=0;clearTimeout(tapTimer);}
                if(!points.size && !moved && !multi){
                    const now=Date.now();
                    if(now-lastTap<300 && lastTapPoint && Math.hypot(e.clientX-lastTapPoint.x,e.clientY-lastTapPoint.y)<44){
                        clearTimeout(tapTimer);lastTap=0;
                        if(scale>base*1.1)reset();else zoomAt(base*2.5,e.clientX,e.clientY);
                    }else{
                        lastTap=now;lastTapPoint={x:e.clientX,y:e.clientY};clearTimeout(tapTimer);
                        tapTimer=setTimeout(function(){window.ImageViewerEvents.showControls();},300);
                    }
                }
                if(moved||multi){lastTap=0;clearTimeout(tapTimer);}
                if(points.size)snapshot();else start=null;
            }
            document.addEventListener('pointerup',up);
            document.addEventListener('pointercancel',up);
            document.addEventListener('wheel',function(e){e.preventDefault();zoomAt(scale*(e.deltaY<0?1.2:1/1.2),e.clientX,e.clientY);},{passive:false});
            document.addEventListener('contextmenu',function(e){e.preventDefault();});
        })();
        </script></body></html>
    """.trimIndent()
}

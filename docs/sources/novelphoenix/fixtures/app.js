
domain_web = window.location.origin;
function toggleFullScreen() {
    var a = window.document,
        b = a.documentElement,
        c = b.requestFullscreen || b.mozRequestFullScreen || b.webkitRequestFullScreen || b.msRequestFullscreen,
        d = a.exitFullscreen || a.mozCancelFullScreen || a.webkitExitFullscreen || a.msExitFullscreen;
    a.fullscreenElement || a.mozFullScreenElement || a.webkitFullscreenElement || a.msFullscreenElement ? d.call(a) : c.call(b)
}
function initializeSearchInput(input) {
    if (input.dataset.clearButtonInitialized === 'true') {
        return;
    }

    var existingClearButton = input.parentNode.querySelector('.clear-input');
    if (existingClearButton) {
        input.dataset.clearButtonInitialized = 'true';
        existingClearButton.setAttribute('role', 'button');
        existingClearButton.setAttribute('tabindex', '0');

        function updateExistingClearButton() {
            existingClearButton.style.opacity = input.value.length > 0 ? '1' : '0';
            existingClearButton.style.pointerEvents = input.value.length > 0 ? 'auto' : 'none';
        }

        function clearExistingInput() {
            input.value = '';
            updateExistingClearButton();
            input.focus();
            input.dispatchEvent(new Event('input', { bubbles: true }));
        }

        input.addEventListener('input', updateExistingClearButton);
        input.addEventListener('change', updateExistingClearButton);
        input.addEventListener('search', updateExistingClearButton);
        existingClearButton.addEventListener('click', clearExistingInput);
        existingClearButton.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                clearExistingInput();
            }
        });
        updateExistingClearButton();
        return;
    }

    var wrapper = document.createElement('span');
    wrapper.className = 'search-input-clear-wrap';
    input.parentNode.insertBefore(wrapper, input);
    wrapper.appendChild(input);

    var clearButton = document.createElement('button');
    clearButton.type = 'button';
    clearButton.className = 'search-input-clear';
    clearButton.setAttribute('title', 'Clear search text');
    clearButton.innerHTML = '<i class="icon-cancel" aria-hidden="true"></i>';
    wrapper.appendChild(clearButton);

    input.dataset.clearButtonInitialized = 'true';

    function updateClearButton() {
        wrapper.classList.toggle('has-value', input.value.length > 0);
    }

    input.addEventListener('input', updateClearButton);
    input.addEventListener('change', updateClearButton);
    input.addEventListener('search', updateClearButton);
    clearButton.addEventListener('click', function () {
        input.value = '';
        updateClearButton();
        input.focus();
        input.dispatchEvent(new Event('input', { bubbles: true }));
    });

    updateClearButton();
}

function initializeSearchInputs(root) {
    if (root.matches && root.matches('input[type="search"], input#inputContent[type="text"]')) {
        initializeSearchInput(root);
    }

    if (root.querySelectorAll) {
        root.querySelectorAll('input[type="search"], input#inputContent[type="text"]').forEach(initializeSearchInput);
    }
}

initializeSearchInputs(document);

new MutationObserver(function (mutations) {
    mutations.forEach(function (mutation) {
        mutation.addedNodes.forEach(function (node) {
            if (node.nodeType === Node.ELEMENT_NODE) {
                initializeSearchInputs(node);
            }
        });
    });
}).observe(document.body, { childList: true, subtree: true });
function clearModal() {
    var existingModal = $('.lnw-modal');
    if (existingModal.length) {
        existingModal.remove();
    }
}
function lockWindow() {
    $("html").css({
        "border-right": window.innerWidth < 768 ? "none" : "17px solid transparent"
    }).addClass("ovh"), $("body").addClass("ovh")
}
function unlockWindow() {
    $("html").removeAttr("style").removeClass("ovh"), $("body").removeClass("ovh")
}
function toggleBodyWrapper(b) {
    var a = $(".main-header")[0].getBoundingClientRect().bottom;
    $(".sidebar-wrapper, .navigation-bar").css({
        top: a <= 0 ? 0 : a
    }), b ? $(".sidebar-wrapper").addClass("show") : $(".sidebar-wrapper").removeClass("show")
}

function toggleMobileNavMenu(a) {
    !$("body").hasClass("navigation") && a ? ($("body").addClass("navigation"), toggleBodyWrapper(!0), lockWindow()) : ($("body").removeClass("navigation"), unlockWindow(), a && toggleBodyWrapper(!1))
}

function closeAllSidebars() {
    $("body").removeClass("navigation"), $(".sidebar-wrapper").removeClass("show"), $("#chapter-article .sidebar").removeClass("show"), unlockWindow()
}

function sort_chapters(b) {
    var a = $(".chapter-list li");
    a.sort(function (d, e) {
        var a = parseInt($(d).data("orderno")),
            c = parseInt($(e).data("orderno"));
        if ("asc" == b) {
            if (a > c) return 1;
            if (a < c) return -1
        } else if ("desc" == b) {
            if (a < c) return 1;
            if (a > c) return -1
        }
        return 0
    }), a.detach().appendTo(".chapter-list")
}

function userInteractShow(a) {
    var b = (new Date).getTime(),
        c = localStorage.getItem(a);
    if (!c) return localStorage.setItem(a, b), !0;
    var d = new Date(Number(c)).getTime();
    return Math.round((b - d) / 6e4) >= 60 && (localStorage.setItem(a, b), !0)
}

function stickyBar(a, b, e, f) {
    var c, g = !1,
        h = !1,
        i = !1,
        d = $(a),
        j = $(b);
    0 != d.length && (c = function () {
        var c = j[0].getBoundingClientRect(),
            a = d[0].getBoundingClientRect(),
            g = d.outerHeight(!0),
            b = e + parseInt($("body").css("marginTop"), 10);
        !h && (a.top < b && !i || a.top > b && i) && (d.css({
            position: "fixed",
            top: b + "px",
            bottom: "",
            width: a.width
        }), h = !0, i = !1, f && j.css("padding-top", g)), c.top > b - (f ? g : 0) && h && (d.css({
            position: "",
            top: "",
            bottom: "",
            width: "auto"
        }), h = !1, i = !1, f && j.css("padding-top", 0)), h && !i && a.bottom > c.bottom && (d.css({
            position: "absolute",
            top: "auto",
            bottom: "0px"
        }), i = !0, h = !1)
    }, $(window).on("scroll", function () {
        g = !0
    }), setInterval(function () {
        g && (c(), g = !1)
    }, 50))
}

function miniSpinner(a, c, d = "static") {
    var b = $(`<div class="loading-spin ${d}" role="status"></div>`);
    c ? (a.addClass("isDisabled"), a.prepend(b)) : ((b = a.find(".loading-spin")).remove(), a.removeClass("isDisabled"))
}

function setLoading(a, b) {
    b ? a.addClass("loading-spin") : a.removeClass("loading-spin")
}

function fn_showloginform() {
    var a = $('<div class="lnw-mod-wrap _loading"><div class="wrap-body"><a href="javascript:;" class="_close"><i class="icon-cancel"></i></a><div class="g_loading"><i></i></div><iframe id="loginIfr" src="/account/login" name="frameLG" id="frameLG" allowtransparentcy="true" width="100%" height="100%" scrolling="no" frameborder="no"></iframe></div></div>');
    $("body").append(a), a.find("iframe").on("load", function () {
        a.removeClass("_loading")
    }), setTimeout(function () {
        a.addClass("_show")
    }, 0)
}

function detectWebDrv() {
    window.webdrv = runBotDetection(), window.webdrv && gtag("event", "selenium", {
        event_category: "browser",
        event_label: "webdriver",
        value: 1
    })
}
var gtag, runBotDetection, speechUtteranceChunker, localStorage = window.localStorage;
window.webdrv = !1, window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches, gtag = gtag || function () { }, $(document).ajaxSend(function (d, a, b) {
    if ("POST" == b.type.toUpperCase()) {
        var c = $("input[name='__LNRequestVerifyToken']").val();
        a.setRequestHeader("LNRequestVerifyToken", c)
    }
}), $.fn.isInViewport = function () {
    var a = $(this).offset().top,
        c = a + $(this).outerHeight(),
        b = $(window).scrollTop(),
        d = b + $(window).height();
    return c > b && a < d
}, $.fn.preventDoubleClick = function () {
    return $(this).on("click", function (b) {
        var a = $(this);
        a.data("clicked") ? (b.preventDefault(), b.stopPropagation()) : (a.addClass("isDisabled"), a.data("clicked", !0), window.setTimeout(function () {
            a.removeData("clicked"), a.removeClass("isDisabled")
        }, 1e3))
    }), this
}, runBotDetection = function () {
    var b, c, d, e, a, f = ["__webdriver_evaluate", "__selenium_evaluate", "__webdriver_script_function", "__webdriver_script_func", "__webdriver_script_fn", "__fxdriver_evaluate", "__driver_unwrapped", "__webdriver_unwrapped", "__driver_evaluate", "__selenium_unwrapped", "__fxdriver_unwrapped",],
        g = ["_phantom", "__nightmare", "_selenium", "callPhantom", "callSelenium", "_Selenium_IDE_Recorder",];
    for (b in g)
        if (c = g[b], window[c]) return !0;
    for (d in f)
        if (e = f[d], window.document[e]) return !0;
    for (a in window.document)
        if (a.match(/\$[a-z]dc_/) && window.document[a].cache_) return !0;
    return !!(window.external && window.external.toString()) && -1 != window.external.toString().indexOf("Sequentum") || !!window.document.documentElement.getAttribute("selenium") || !!window.document.documentElement.getAttribute("webdriver") || !!window.document.documentElement.getAttribute("driver") || !!/HeadlessChrome/.test(navigator.userAgent) || !navigator.languages || 0 === navigator.languages.length
}, detectWebDrv(), speechUtteranceChunker = function (a, c, h) {
    var b, f, i, d;
    if (f = void 0 !== (c = c || {}).offset ? a.text.substring(c.offset) : a.text, a.voice && "native" === a.voice.voiceURI) (b = a).text = f, b.addEventListener("end", function () {
        speechUtteranceChunker.cancel && (speechUtteranceChunker.cancel = !1), void 0 !== h && h()
    });
    else {
        var e = c && c.chunkLength || 160,
            j = new RegExp("^[\\s\\S]{" + Math.floor(e / 2) + "," + e + "}[.!?,]{1}|^[\\s\\S]{1," + e + "}$|^[\\s\\S]{1," + e + "} "),
            g = f.match(j);
        if (void 0 === g[0] || g[0].length <= 2) {
            void 0 !== h && h();
            return
        }
        for (d in i = g[0], (b = new SpeechSynthesisUtterance(i)).voice = a.voice, b.pitch = a.pitch, b.rate = a.rate, b.volume = a.volume, a) a.hasOwnProperty(d) && "text" !== d && (b[d] = a[d]);
        b.addEventListener("end", function () {
            if (speechUtteranceChunker.cancel) {
                speechUtteranceChunker.cancel = !1;
                return
            }
            c.offset = c.offset || 0, c.offset += i.length - 1, speechUtteranceChunker(a, c, h)
        })
    }
    c.modifier && c.modifier(b), console.log(b), setTimeout(function () {
        speechSynthesis.speak(b)
    }, 0)
}, $(document).ready(function () {
    if ($("#mobile-menu-btn").click(function () {
        toggleMobileNavMenu(!0)
    }), window.innerWidth < 1024 && $(".nav-item.dropdown").click(function () {
        var a = $(this);
        a.hasClass("_on") ? a.removeClass("_on") : a.addClass("_on")
    }), $(".sidebar-wrapper").click(function () {
        closeAllSidebars()
    }), $("body").on("keyup", "#aboutyou", function (b) {
        var a = $(b.currentTarget),
            c = parseInt(a.attr("maxlength")),
            d = a.val().length;
        $(".int-about i").html(`${d}/${c}`)
    }), $(document).on("click", ".reply-comments .show_replies", function (e) {
        var o = $(this).closest(".reply-comments");
        o.hasClass("expanded") ? (o.removeClass("expanded"), $([document.documentElement, document.body]).animate({
            scrollTop: o.closest("li").offset().top
        }, 200), $(this).html("Show More Replies")) : (o.addClass("expanded"), $([document.documentElement, document.body]).animate({
            scrollTop: o.offset().top
        }, 200), $(this).html("Close Replies"), typeof initReadMore === 'function' && initReadMore(o[0]))
    }), $("main").on("click", ".lnw-modal-soft .modal-section ._close_soft", function () {
        $(".lnw-modal-soft").removeClass("_show"), unlockWindow()
    }), $("#novel")[0] && ($("#chorder").click(function () {
        var a = $(this).data("order"),
            b = sort_chapters(a);
        $.when(b).done(function () {
            var a = $("#chorder");
            "desc" == a.data("order") ? (a.removeClass("fa-sort-amount-down-alt").addClass("fa-sort-amount-up"), a.data("order", "asc")) : (a.removeClass("fa-sort-amount-up").addClass("fa-sort-amount-down-alt"), a.data("order", "desc"))
        })
    }), window.innerWidth >= 1654 && $(".ad-container .ad-sticky")[0] && stickyBar(".ad-container .ad-sticky", ".ad-container", 10, !1), window.innerWidth >= 1654 && $(".ad-container.left .ad-sticky")[0] && stickyBar(".ad-container-left .ad-sticky", ".ad-container.left", 10, !1), $(".expand-wrapper .expand-btn").on("click", function () {
        var w = $(this).closest(".expand-wrapper");
        w.toggleClass("show");
        if (!w.hasClass("show")) {
            w[0].scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
        $(this).find("span").text(w.hasClass("show") ? "Show Less" : "Show More");
    })), $("#chapter-article")[0]) {

    }
    $(".nf-ads-fixed-bt .closebtn").click(function () {
        $(".nf-ads-fixed-bt").removeClass("_on")
    }), $(".view-more").click(function () {
        $(this).css("max-height", "inherit"), $(this).removeClass("expose")
    }), $(".nav-back, .bar-nav-back").click(function () {
        if ($("#chapter-article")[0]) {
            var a = window.location.pathname.substring(0, window.location.pathname.lastIndexOf("/"));
            window.location = a
        } else window.history.back()
    }), $(".alertbox .closebtn, .alertbox .apply").click(function () {
        $(".alertbox").removeClass("show"), $("html").removeAttr("style")
    }), $(document).on("click", "a.lnw-login-act", function () {
        fn_showloginform(), $(".alertbox .closebtn").trigger("click")
    }), $(".notice-bar .close i").on("click", function () {
        $(".notice-bar").remove()
    }), $(document).on("click", ".lnw-mod-wrap ._close", function () {
        $(".lnw-mod-wrap").removeClass("_show"), setTimeout(function () {
            $(".lnw-mod-wrap").remove()
        }, 100)
    }), $("#comment-policy-show").on("click", function () {
        $.get("/account/comterm", null, function (a) {
            a.success && (lockWindow(), $("main").append(a.content), $("main #lnwcomtermpanel").addClass("_show"))
        })
    }), $(document).on("click", ".spoiler-area .warn", function () {
        $(this).closest(".review-text"), $(this).find("i");
        var a = $(this).closest(".spoiler-area");
        a.hasClass("show") ? a.removeClass("show") : a.addClass("show")
    });
    setTimeout(function () {
        $(".expand-wrapper").each(function () {
            var mh = parseInt($(this).css("max-height")) || 0;
            if (mh > 0 && this.scrollHeight <= mh + 20) { $(this).addClass("no-expand"); }
        });
    }, 50);
    var h, i, j = 0,
        k = $(".main-header").outerHeight();
    $(window).scroll(function (a) {
        i = !0
    }), setInterval(function () {
        i && (function () {
            var a = $(this).scrollTop();
            5 >= Math.abs(j - a) || (a > j || a < k ? $("#back-to-top").removeClass("show") : a + $(window).height() < $(document).height() && $("#back-to-top").addClass("show"), j = a)
        }(), i = !1)
    }, 250)
})

$("[data-dyscroll='1']").each(function (e, o) {
    var t = $(o).width();
    $(o).find("*[scrollpanel]")[0].scrollWidth <= t && ($(o).find("*[data-scroll-act]").css("display", "none"), $(o).addClass("nosc"))
})
$("i[data-scroll-act]").on("click", function (e) {
    e.preventDefault();
    var e = $(this).data("scroll-act"),
        o = $(this).data("scroll-id"),
        o = $("#" + o),
        t = o.scrollLeft();
    "left" === e ? o.animate({
        scrollLeft: t - 100
    }, "fast") : "right" === e && o.animate({
        scrollLeft: t + 100
    }, "fast")
})
$(document).on('click', '.closebtn', function () {
    $('.alertbox').removeClass('show');
    unlockWindow();
})
$(document).on('click', '._close', function () {
    $('.lnw-modal').remove();
    unlockWindow();
})
$("#novel-report").on("click", function () {
    var form = '<div class="form-report lnw-modal _show"><section class="modal-section"><a href="javascript:;" class="_close"><i class="icon-cancel"></i></a><div class="modal-header">Report Novel Issue</div><div class="edit-form minimal"><form id="report-form" method="post"><div id="formResult"><div class="fieldset"><label class="_lbl fw">Enter the text of your complaint:</label><div class="field-col about fw"><div class="int-about"><textarea id="aboutyou" class="_int" cols="30" rows="8" maxlength="500" placeholder="Provide all details about the issue" name="ReportDetail"></textarea><i>0/500</i></div><span class="ln_vali_tip field-validation-valid"></span></div></div><div class="fieldset"><div class="field-col btnfield text-center pt-3"><button id="reportSubmitBtn" class="button" type="button">Submit Report</button></div></div></div></form></div></section></div>';
    $('body').append(form);
})

function sendReport(content_report) {
    const reportMessage = `USER REPORT: ${content_report} URL: ${document.URL}`;

    $.post('/api/send-telegram', {
        message: reportMessage,
        _token: $('meta[name="csrf-token"]').attr('content')
    }, function (data) {
        if (data.status === 200) {
            $('.form-report.lnw-modal').remove();
            $('body').append('<div class="lnw-modal _show"><section class="modal-section"><a href="javascript:;" class="_close"><i class="icon-cancel"></i></a><div class="modal-header">Report Novel Issue</div><div class="edit-form minimal"><form id="report-form" method="post"><div id="formResult"><style>p{margin:16px 0}</style><p>Thank you for your attention.</p><p>Your message has been successfully sent.</p></div></form></div></section></div>');
        } else {
            console.error('Error sending report to Telegram.');
            alert('Error sending report. Please try again.');
        }
    }).fail(function () {
        console.error('Error sending report to Telegram.');
        alert('Error sending report. Please try again.');
    });
}
$(document).on('click', '#reportSubmitBtn', function () {
    var post_id = $("#novel-report").attr("report-post_id");
    var chapter_id = $("#novel-report").attr("report-chapter_id");
    var content_report = $('#aboutyou').val().trim();
    var error = '';
    if (content_report == '') {
        error = 'The Report Details field is required.';
    } else if (content_report.length < 10) {
        error = 'Minimum 10 characters required.';
    } else {
        $('#reportSubmitBtn').addClass("isDisabled").append('&nbsp<div class="loading-dual-ring small blue"></div>');
        content_report = 'USER_ID: ' + $('a.notification').data('id') + ' - ' + content_report;
        sendReport(content_report);
        $.ajax({
            method: "POST",
            url: domain_web + '/api/report',
            data: { post_id: post_id, chapter_id: chapter_id, content: content_report, _token: $('meta[name="csrf-token"]').attr('content') },
            dataType: 'json',
            success: function (response) {
                if (response.status == 200) {
                    $('.form-report.lnw-modal').remove();
                    $('body').append('<div class="lnw-modal _show"><section class="modal-section"><a href="javascript:;" class="_close"><i class="icon-cancel"></i></a><div class="modal-header">Report Novel Issue</div><div class="edit-form minimal"><form id="report-form" method="post"><div id="formResult"><style>p{margin:16px 0}</style><p>Thank you for your attention.</p><p>Your message has been successfully sent.</p></div></form></div></section></div>');
                } else {
                    alert('Error sending report. Please try again.');
                }
            },
        });
    }
    $('.field-validation-valid').html(error);
})
function notificationBadge(comment_id) {
    $('#lnw-comment-' + comment_id + ' .comment-header').after('<div class="notification-badge"><i class="icon-bell"></i></div>');
}
function scrollToElementOnPage(selector, offset = 75) {
    const urlParams = new URLSearchParams(window.location.search);

    if (['page', 'filter', 'keyword', 'post_id', 'order', 'review_id'].some(p => urlParams.has(p))) {
        if ($(selector).length) {
            $('html,body').animate({
                scrollTop: $(selector).offset().top - offset
            }, 'slow');
        } else {
            selector = 'p.fs16';
            $(selector).length && $('html,body').animate({
                scrollTop: $(selector).offset().top - offset
            }, 'slow');
        }
    } else if (urlParams.has('comid')) {
        $('html,body').animate({
            scrollTop: $(selector).offset().top
        }, 'slow');
    }
}
function initCommentLoader(scrollOffset) {
    let scriptLoading = false;
    let commentLoaded = false;
    $(window).on('scroll', function () {
        if (!scriptLoading) {
            scriptLoading = true;
            $('<link/>', { rel: "stylesheet", type: "text/css", href: domain_web + '/frontend/css/comments.min.css?ver=' + VERSION }).appendTo('head');
            $('<script/>', { src: domain_web + '/frontend/js/comment.min.js?ver=' + VERSION, type: "text/javascript" }).appendTo('head');
        }
        if (!commentLoaded) {
            const $target = $('#chapter-comments');
            if ($target.length === 0) return;
            const hT = $target.offset().top;
            const hH = $target.outerHeight();
            const wH = $(window).height();
            const wS = $(this).scrollTop();
            if (wS + scrollOffset > (hT + hH - wH)) {
                commentLoaded = true;
                if (typeof ajaxComment === "function") {
                    ajaxComment();
                } else {
                    let attempts = 0;
                    const maxAttempts = 100; // Timeout after 5 seconds (100 * 50ms)
                    const waitScript = setInterval(() => {
                        attempts++;
                        if (typeof ajaxComment === "function") {
                            clearInterval(waitScript);
                            ajaxComment();
                        } else if (attempts >= maxAttempts) {
                            clearInterval(waitScript);
                            console.error('ajaxComment function not found after 5s');
                        }
                    }, 50);
                }
            }
        }
    });
}
function loadFormComment() {
    $('<script/>', { type: "text/javascript", src: "https://cdnjs.cloudflare.com/ajax/libs/tinymce/7.6.0/tinymce.min.js" }).appendTo('head');
    form = `<section class="comment-list">
                <div class="head">
                    <h4>User Comments</h4>
                    <a href="javascript:;" id="write-comment" data-submit="false" class="button">Write Comment</a>
                </div>
                <div class="comment-policy">
                    <a id="comment-policy-show" href="javascript:;">Please read and apply the rules before posting a comment.</a><br>
                    By sharing your comment, you agree to all the relevant terms.
                </div>
                <div id="comment-filter">
                    <div class="radio-group">
                        <div class="sub-group">
                            <div class="group-items">
                                <div class="radio-item text-center" data-orderby="newest">
                                    <input id="com_order_newest" type="radio" name="com_order" checked><label for="com_order_newest">Newest</label>
                                </div>
                            </div>
                        </div>
                        <div class="sub-group">
                            <div class="group-items">
                                <div class="radio-item text-center" data-orderby="mostliked">
                                    <input id="com_order_mostliked" type="radio" name="com_order"><label for="com_order_mostliked">Most Liked</label>
                                </div>
                                <div class="radio-item text-center" data-orderby="oldest">
                                    <input id="com_order_oldest" type="radio" name="com_order"><label for="com_order_oldest">Oldest</label>
                                </div>
                            </div>
                        </div>
                        <button type="button" id="refresh-comments" title="Refresh comments"><i class="icon-arrows-cw"></i></button>
                    </div>
                </div>
                <div class="no-com d-none">No comments yet. Join the community and start a discussion thread.</div>
                <div class="comment-wrapper">
                    <ul></ul>
                    <div class="load-more"><a id="lmcomments" class="button d-none">Load More Comments</a></div>
                </div>
            </section>`;
    $("#chapter-comments").html(form)
    $("#chapter-comments ul").html('<div class="loading-spin static xl center" role="status"></div><p class="text-center pt-2">Comments loading...</p>')
}
$(document).on("click", ".comment-text[data-spoiler='1'], .reply-comment[data-spoiler='1'], .review-text[data-spoiler='1']", function (e) {
    $(this).data("spoiler", 0), $(this).attr("data-spoiler", 0)
})
function processHTML(content) {
    const $container = $('<div></div>').html(content);
    $container.find('p').each(function () {
        if ($(this).text().trim() === '') {
            $(this).remove();
        }
    });
    $container.find('a').each(function () {
        const $a = $(this);
        const href = $a.attr('href') || '';
        const text = $a.text().trim();
        if (!text.startsWith('@')) {
            $a.replaceWith(document.createTextNode(text));
            return;
        }
        if (!href.includes('/user/')) {
            $a.replaceWith(document.createTextNode(text));
            return;
        }
    });
    return $container.html().replace(/>\s+</g, '><').trim();
}

// image lazyload
function initLazyLoad() { var e, t, n; function o() { n && clearTimeout(n), n = setTimeout(function () { var n = window.pageYOffset; e.forEach(function (e) { e.offsetTop < window.innerHeight + n && (e.src = e.dataset.src, e.classList.remove("lazy")) }), 0 == e.length && (document.removeEventListener("scroll", o), window.removeEventListener("resize", o), window.removeEventListener("orientationChange", o)) }, 20) } "IntersectionObserver" in window ? (e = document.querySelectorAll(".lazy"), t = new IntersectionObserver(function (e, n) { e.forEach(function (e) { e.isIntersecting && ((e = e.target).src = e.dataset.src, e.classList.remove("lazy"), t.unobserve(e)) }) }), e.forEach(function (e) { t.observe(e) })) : (e = document.querySelectorAll(".lazy"), document.addEventListener("scroll", o), window.addEventListener("resize", o), window.addEventListener("orientationChange", o)) }
document.addEventListener("DOMContentLoaded", function () { initLazyLoad() });
// star
!function (t, s, e, i) { "use strict"; var a = function () { }, r = { totalStars: 5, useFullStars: !1, starShape: "straight", emptyColor: "lightgray", hoverColor: "orange", activeColor: "gold", ratedColor: "crimson", useGradient: !0, readOnly: !1, disableAfterRate: !0, baseUrl: !1, starGradient: { start: "#FEF7CD", end: "#FF9511" }, strokeWidth: 4, strokeColor: "black", initialRating: 0, starSize: 40, callback: a, onHover: a, onLeave: a }, n = function (s, e) { var i, a; this.element = s, this.$el = t(s), this.settings = t.extend({}, r, e), i = this.$el.data("rating") || this.settings.initialRating, a = ((this.settings.forceRoundUp ? Math.ceil : Math.round)(2 * i) / 2).toFixed(1), this._state = { rating: a }, this._uid = Math.floor(999 * Math.random()), e.starGradient || this.settings.useGradient || (this.settings.starGradient.start = this.settings.starGradient.end = this.settings.activeColor), this._defaults = r, this._name = "starRating", this.init() }, o = { init: function () { this.renderMarkup(), this.addListeners(), this.initRating() }, addListeners: function () { this.settings.readOnly || (this.$stars.on("mouseover", this.hoverRating.bind(this)), this.$stars.on("mouseout", this.restoreState.bind(this)), this.$stars.on("click", this.handleRating.bind(this))) }, hoverRating: function (t) { var s = this.getIndex(t); this.paintStars(s, "hovered"), this.settings.onHover(s + 1, this._state.rating, this.$el) }, handleRating: function (t) { var s = this.getIndex(t) + 1; this.applyRating(s, this.$el), this.executeCallback(s, this.$el), this.settings.disableAfterRate && this.$stars.off() }, applyRating: function (t) { var s = t - 1; this.paintStars(s, "rated"), this._state.rating = s + 1, this._state.rated = !0 }, restoreState: function (t) { var s = this.getIndex(t), e = this._state.rating || -1, i = this._state.rated ? "rated" : "active"; this.paintStars(e - 1, i), this.settings.onLeave(s + 1, this._state.rating, this.$el) }, getIndex: function (s) { var e = t(s.currentTarget), i = e.width(), a = t(s.target).attr("data-side"), r = this.settings.minRating; a = a || this.getOffsetByPixel(s, e, i), a = this.settings.useFullStars ? "right" : a; var n = e.index() - ("left" === a ? .5 : 0); return n = n < .5 && s.offsetX < i / 4 ? -1 : n, n = r && r <= this.settings.totalStars && n < r ? r - 1 : n }, getOffsetByPixel: function (t, s, e) { return t.pageX - s.offset().left <= e / 2 && !this.settings.useFullStars ? "left" : "right" }, initRating: function () { this.paintStars(this._state.rating - 1, "active") }, paintStars: function (s, e) { var i, a, r, n, o = this.settings; t.each(this.$stars, function (l, h) { i = t(h).find('[data-side="left"]'), a = t(h).find('[data-side="right"]'), r = n = l <= s ? e : "empty", r = l - s == .5 ? e : r, i.attr("class", "svg-" + r + "-" + this._uid), a.attr("class", "svg-" + n + "-" + this._uid); var d, g = s >= 0 ? Math.ceil(s) : 0; d = o.ratedColors && o.ratedColors.length && o.ratedColors[g] ? o.ratedColors[g] : this._defaults.ratedColor, "rated" === e && s > -1 && ((l <= Math.ceil(s) || l < 1 && s < 0) && i.attr("style", "fill:" + d), l <= s && a.attr("style", "fill:" + d)) }.bind(this)) }, renderMarkup: function () { for (var t = this.settings, s = t.baseUrl ? location.href.split("#")[0] : "", e = '<div class="jq-star" style="width:' + t.starSize + "px;  height:" + t.starSize + 'px;"><svg version="1.0" class="jq-star-svg" shape-rendering="geometricPrecision" xmlns="http://www.w3.org/2000/svg" ' + this.getSvgDimensions(t.starShape) + " stroke-width:" + t.strokeWidth + 'px;" xml:space="preserve"><style type="text/css">.svg-empty-' + this._uid + "{fill:url(" + s + "#" + this._uid + "_SVGID_1_);}.svg-hovered-" + this._uid + "{fill:url(" + s + "#" + this._uid + "_SVGID_2_);}.svg-active-" + this._uid + "{fill:url(" + s + "#" + this._uid + "_SVGID_3_);}.svg-rated-" + this._uid + "{fill:" + t.ratedColor + ";}</style>" + this.getLinearGradient(this._uid + "_SVGID_1_", t.emptyColor, t.emptyColor, t.starShape) + this.getLinearGradient(this._uid + "_SVGID_2_", t.hoverColor, t.hoverColor, t.starShape) + this.getLinearGradient(this._uid + "_SVGID_3_", t.starGradient.start, t.starGradient.end, t.starShape) + this.getVectorPath(this._uid, { starShape: t.starShape, strokeWidth: t.strokeWidth, strokeColor: t.strokeColor }) + "</svg></div>", i = "", a = 0; a < t.totalStars; a++)i += e; this.$el.append(i), this.$stars = this.$el.find(".jq-star") }, getVectorPath: function (t, s) { return "rounded" === s.starShape ? this.getRoundedVectorPath(t, s) : this.getSpikeVectorPath(t, s) }, getSpikeVectorPath: function (t, s) { return '<polygon data-side="center" class="svg-empty-' + t + '" points="281.1,129.8 364,55.7 255.5,46.8 214,-59 172.5,46.8 64,55.4 146.8,129.7 121.1,241 212.9,181.1 213.9,181 306.5,241 " style="fill: transparent; stroke: ' + s.strokeColor + ';" /><polygon data-side="left" class="svg-empty-' + t + '" points="281.1,129.8 364,55.7 255.5,46.8 214,-59 172.5,46.8 64,55.4 146.8,129.7 121.1,241 213.9,181.1 213.9,181 306.5,241 " style="stroke-opacity: 0;" /><polygon data-side="right" class="svg-empty-' + t + '" points="364,55.7 255.5,46.8 214,-59 213.9,181 306.5,241 281.1,129.8 " style="stroke-opacity: 0;" />' }, getRoundedVectorPath: function (t, s) { var e = "M520.9,336.5c-3.8-11.8-14.2-20.5-26.5-22.2l-140.9-20.5l-63-127.7 c-5.5-11.2-16.8-18.2-29.3-18.2c-12.5,0-23.8,7-29.3,18.2l-63,127.7L28,314.2C15.7,316,5.4,324.7,1.6,336.5S1,361.3,9.9,370 l102,99.4l-24,140.3c-2.1,12.3,2.9,24.6,13,32c5.7,4.2,12.4,6.2,19.2,6.2c5.2,0,10.5-1.2,15.2-3.8l126-66.3l126,66.2 c4.8,2.6,10,3.8,15.2,3.8c6.8,0,13.5-2.1,19.2-6.2c10.1-7.3,15.1-19.7,13-32l-24-140.3l102-99.4 C521.6,361.3,524.8,348.3,520.9,336.5z"; return '<path data-side="center" class="svg-empty-' + t + '" d="' + e + '" style="stroke: ' + s.strokeColor + '; fill: transparent; " /><path data-side="right" class="svg-empty-' + t + '" d="' + e + '" style="stroke-opacity: 0;" /><path data-side="left" class="svg-empty-' + t + '" d="M121,648c-7.3,0-14.1-2.2-19.8-6.4c-10.4-7.6-15.6-20.3-13.4-33l24-139.9l-101.6-99 c-9.1-8.9-12.4-22.4-8.6-34.5c3.9-12.1,14.6-21.1,27.2-23l140.4-20.4L232,164.6c5.7-11.6,17.3-18.8,30.2-16.8c0.6,0,1,0.4,1,1 v430.1c0,0.4-0.2,0.7-0.5,0.9l-126,66.3C132,646.6,126.6,648,121,648z" style="stroke: ' + s.strokeColor + '; stroke-opacity: 0;" />' }, getSvgDimensions: function (t) { return "rounded" === t ? 'width="550px" height="500.2px" viewBox="0 146.8 550 500.2" style="enable-background:new 0 0 550 500.2;' : 'x="0px" y="0px" width="305px" height="305px" viewBox="60 -62 309 309" style="enable-background:new 64 -59 305 305;' }, getLinearGradient: function (t, s, e, i) { return '<linearGradient id="' + t + '" gradientUnits="userSpaceOnUse" x1="0" y1="-50" x2="0" y2="' + ("rounded" === i ? 500 : 250) + '"><stop  offset="0" style="stop-color:' + s + '"/><stop  offset="1" style="stop-color:' + e + '"/> </linearGradient>' }, executeCallback: function (t, s) { (0, this.settings.callback)(t, s) } }, l = { unload: function () { var s = "plugin_starRating", e = t(this); e.data(s).$stars.off(), e.removeData(s).remove() }, setRating: function (s, e) { var i = t(this).data("plugin_starRating"); s > i.settings.totalStars || s < 0 || (e && (s = Math.round(s)), i.applyRating(s)) }, getRating: function () { return t(this).data("plugin_starRating")._state.rating }, resize: function (s) { var e = t(this).data("plugin_starRating").$stars; s <= 1 || s > 200 ? console.error("star size out of bounds") : (e = Array.prototype.slice.call(e)).forEach(function (e) { t(e).css({ width: s + "px", height: s + "px" }) }) }, setReadOnly: function (s) { var e = t(this).data("plugin_starRating"); !0 === s ? e.$stars.off("mouseover mouseout click") : (e.settings.readOnly = !1, e.addListeners()) } }; t.extend(n.prototype, o), t.fn.starRating = function (s) { if (!t.isPlainObject(s)) { if (l.hasOwnProperty(s)) return l[s].apply(this, Array.prototype.slice.call(arguments, 1)); t.error("Method " + s + " does not exist on starRating.js") } return this.each(function () { t.data(this, "plugin_starRating") || t.data(this, "plugin_starRating", new n(this, s)) }) } }(jQuery, window, document);
$(".info-rating").starRating({ totalStars: 5, starShape: "rounded", starSize: 16, readOnly: !0 })
function showToast(message, type = "success", reload = false) {
    const container = document.getElementById("toast-container");
    const toast = document.createElement("div");
    toast.className = `toast ${type}`;
    let icon;
    switch (type) {
        case "success":
            icon = "✅";
            break;
        case "error":
            icon = "❌";
            break;
        case "warning":
            icon = "⚠️";
            break;
        default:
            icon = "ℹ️";
    }

    toast.innerHTML = `
    <div class="toast-content skiptranslate">
      <span class="icon">${icon}</span>
      <span>${message}</span>
    </div>
    <button>&times;</button>
  `;

    const closeBtn = toast.querySelector("button");
    closeBtn.addEventListener("click", () => {
        toast.remove();
        if (reload) location.reload();
    });

    container.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = "fadeOut 0.3s ease forwards";
        toast.remove();
        if (reload) location.reload();
    }, 3000);
}

function highlightElement(targetEl, elements = [], offset = 0, center = true) {
    if (!targetEl) return;

    if (Array.isArray(elements) && elements.length > 0) {
        elements.forEach(el => el.style.backgroundColor = '');
    }

    targetEl.style.backgroundColor = 'var(--bg-color)';
    targetEl.style.transition = 'background-color 0.5s ease';

    const rect = targetEl.getBoundingClientRect();
    const y = window.scrollY + rect.top - (center ? (window.innerHeight / 2 - rect.height / 2) : offset);

    window.scrollTo({ top: y, behavior: 'smooth' });

    setTimeout(() => {
        targetEl.style.backgroundColor = '';
    }, 1500);
}
$('.search-form').on('submit', function (e) {
    e.preventDefault();
    searchInput();
});

document.addEventListener('click', function (e) {
    if (!e.target.closest('.dropdown')) {
        document.querySelectorAll('.dropdown._on').forEach(item => {
            item.classList.remove('_on');
        });
    }
    if (!e.target.closest('#novelListBase')) {
        const novelList = document.querySelector('#novelListBase');
        if (novelList) {
            novelList.innerHTML = '';
        }
    }

    if (!filterBtn.contains(e.target)) filterMenu.style.display = "none";
});
var typingTimer;
var $inputSearch = $('#inputContent');
$inputSearch.on('keyup paste', function (e) {
    clearTimeout(typingTimer);
    if ((e.keyCode || e.which) != 13) {
        typingTimer = setTimeout(searchInput, 500);
    }
});
$inputSearch.on('keydown', function () {
    clearTimeout(typingTimer);
});
function searchInput() {
    var keyword = $inputSearch.val().trim();
    if (keyword.length == 0) {
        $("#novelListBase").html(''); return;
    }
    if (keyword.length < 3) return;
    $("#novelListBase").html("<div class='loading-dual-ring small blue my-3'></div>");
    let type = $("#filterDropdownMenu li.active").data("value") || "title";
    $.get('/ajax/searchLive', { keyword: keyword, type: type }).done(function (res) {
        let html = '';
        if (res.data.length > 0) {
            html += `<ul class="novel-list horizontal col1">`;
            res.data.slice(0, 5).forEach(function (value) {
                html += `<li class="novel-item" style="width: 100%"><a href="/novel/${value.slug}"><div class="cover-wrap"><figure class="novel-cover"><img class="lazy" src="data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==" data-src="${domain_web + '/' + value.image}"></figure></div><div class="item-body"><h4 class="novel-title text1row">${value.title}</h4><div class="novel-stats"><strong><i class="icon-crown"></i> Rank ${value.rank}</strong></div><div class="novel-stats"><span><i class="icon-book-open"></i> ${value.total_chapter} Chapters</span></div></div></a></li>`;
            });
            html += `</ul>`;
            if (res.data.length > 5) {
                html += `<div class="text-center mt-2 mb-3"><a class="button" href="/search?keyword=${encodeURIComponent(keyword)}&type=${type}">View all Results</a></div>`;
            }
        } else {
            html = `<p class="text-center py-3">No results for your search.</p>`;
        }
        $("#novelListBase").html(html); initLazyLoad();
    }).fail(function () {
        $("#novelListBase").html(`<p class="text-center py-3">An error occurred. Please refresh the page.</p>`);
    });
}

const filterBtn = document.getElementById("filterDropdownBtn");
const filterMenu = document.getElementById("filterDropdownMenu");
const filterLabel = document.getElementById("filterLabel");

filterBtn.addEventListener("click", () => {
    filterMenu.style.display = filterMenu.style.display === "block" ? "none" : "block";
});

filterMenu.querySelectorAll("li").forEach(item => {
    item.addEventListener("click", () => {
        filterMenu.querySelector(".active")?.classList.remove("active");
        item.classList.add("active");
        filterLabel.textContent = item.textContent;
        filterMenu.style.display = "none";
        const value = item.getAttribute("data-value");
        if (value === "title") {
            $inputSearch.attr("placeholder", "Search by title...");
        } else if (value === "author") {
            $inputSearch.attr("placeholder", "Search by author...");
        } else if (value === "both") {
            $inputSearch.attr("placeholder", "Search by title or author...");
        }

        requestAnimationFrame(() => {
            searchInput();
        });
    });

});

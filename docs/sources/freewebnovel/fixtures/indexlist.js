(function(window, document, $) {
    'use strict';

    var pageRoot = document.getElementById('indexListPage');
    if (!pageRoot || !$) return;

	var config = {
	    articleId: parseInt(pageRoot.getAttribute('data-article-id'), 10) || 0,
	    score: parseFloat(pageRoot.getAttribute('data-score')) || 0,
	    ratingLoggedIn: pageRoot.getAttribute('data-rating-logged-in') === '1',
	    ratingLimit: parseInt(pageRoot.getAttribute('data-rating-limit'), 10) || 10,
	    ratingUsage: parseInt(getCookie('rating_usage'), 10) || 0,
	    currentPage: parseInt(pageRoot.getAttribute('data-current-page'), 10) || 1,
        pageSize: parseInt(pageRoot.getAttribute('data-page-size'), 10) || 40,
        totalPage: parseInt(pageRoot.getAttribute('data-total-page'), 10) || 1,
        totalChapters: parseInt(pageRoot.getAttribute('data-total-chapters'), 10) || 0
    };

    function getCookie(name) {
        var match = document.cookie.match(new RegExp('(?:^|;\\s*)' + name + '=([^;]*)'));
        if (!match) return '';
        try {
            return decodeURIComponent(match[1]);
        } catch (e) {
            return match[1];
        }
    }

    function initBookcase() {
        if (typeof BookCase !== 'function') return;
        new BookCase({
            attrArticleid: 'data-articleid',
            attrChapterid: 'data-chapterid',
            parentSelecter: 'body',
            selecter: '.set-case',
            selectedClass: '.on',
            delUrl: '/api/delbookcase.php',
            addUrl: '/api/addbookcase.php',
            delCallback: function(element) {
                element.removeClass('on').html(
                    '<span class="glyphicon glyphicon-plus"></span>' +
                    ' <span class="hide-on-mobile">Add to Library</span>'
                );
            },
            addCallback: function(element) {
                element.addClass('on').html(
                    '<span class="glyphicon glyphicon-ok"></span>' +
                    ' <span class="hide-on-mobile">In Library</span>'
                );
            },
            selecterBef: function(element) {
                var content = '<span class="glyphicon glyphicon-refresh spin"></span> Loading...';
                if ($(window).width() <= 480) {
                    content = '<span class="glyphicon glyphicon-refresh spin"></span>';
                }
                $(element).addClass('btn-loading').prop('disabled', true).html(content);
            },
            selecterAft: function(element) {
                $(element).removeClass('btn-loading').prop('disabled', false);
            }
        });
    }

    function getRatedBooks() {
        var value = getCookie('book');
        if (!value) return [];
        return value.split('|').filter(function(id) {
            return id !== '';
        });
    }

	function initRating() {
        var stars = document.querySelectorAll('.stars-section li');
        var starsBox = document.querySelector('.stars-section');
        if (!stars.length || !starsBox || !config.articleId) return;

        var fullStars = Math.min(stars.length, Math.floor(config.score));
        var ratedBooks = getRatedBooks();
        var alreadyRated = ratedBooks.indexOf(String(config.articleId)) !== -1;
        var index;

        for (index = 0; index < fullStars; index++) {
            stars[index].classList.add('active');
        }
        if (config.score - fullStars >= 0.5 && fullStars < stars.length) {
            stars[fullStars].classList.add('active-half');
        }

	    $('.score').append('<p class="vote">' +
	        (!config.ratingLoggedIn ? 'Log in to rate' :
	            (alreadyRated ? 'Thank you for voting!' : 'Your Rating?')) + '</p>');
	    if (!config.ratingLoggedIn) {
	        starsBox.style.cursor = 'pointer';
	        Array.prototype.forEach.call(stars, function(star) {
	            star.addEventListener('click', function() {
                if (typeof window.loginPopups === 'function') window.loginPopups(this);
	            });
	        });
	        return;
	    }
	    if (alreadyRated || config.ratingUsage >= config.ratingLimit ||
	        ratedBooks.length >= config.ratingLimit || !window.fetch) return;

        starsBox.style.cursor = 'pointer';
        var submitting = false;
        function preview(score, active) {
            for (var i = 0; i < stars.length; i++) {
                stars[i].style.filter = active && i <= score ? 'brightness(1.2)' : '';
            }
        }
        function submitRating(score) {
            if (submitting) return;
            submitting = true;
            starsBox.style.pointerEvents = 'none';
            fetch('/api/rate.php', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: 'sourceid=' + encodeURIComponent(config.articleId) +
                    '&score=' + encodeURIComponent(score)
            })
                .then(function(response) {
                    return response.json().then(function(data) {
                        if (!response.ok || !data || data.code !== 0) {
                            throw new Error(data && data.msg ? data.msg : 'Unable to submit your rating.');
                        }
                        return data;
                    });
                })
		            .then(function() {
                    window.location.reload();
                })
                .catch(function(error) {
                    submitting = false;
                    starsBox.style.pointerEvents = '';
                    alert(error && error.message ? error.message : 'Unable to submit your rating. Please try again.');
                });
        }

        Array.prototype.forEach.call(stars, function(star, starIndex) {
            star.addEventListener('mouseenter', function() {
                preview(starIndex, true);
            });
            star.addEventListener('mouseleave', function() {
                preview(starIndex, false);
            });
	        star.addEventListener('click', function() {
	            var score = starIndex + 1;
	            if (window.confirm('Rate this novel ' + score + ' out of 5?')) {
	                submitRating(score);
	            }
	        });
        });
    }

    function initDescription() {
        $('.js-link').on('click.indexList', function(e) {
            e.preventDefault();
            var target = $($(this).data('id'));
            if (!target.length) return;
            $('html, body').animate({scrollTop: target.offset().top}, 'slow');
        });
        $('body').on('click.indexList', '.js-show', function() {
            $(this).closest('.m-desc').addClass('open');
        }).on('click.indexList', '.js-hide', function() {
            $(this).closest('.m-desc').removeClass('open');
        });
        $('.m-desc').each(function() {
            if ($(this).find('.inner').height() > 180) $(this).addClass('hasmore');
        });
    }

    function initPagination() {
        var pageSize = config.pageSize;
        var totalPage = config.totalPage;
        var totalChapters = config.totalChapters;
        var currentPage = config.currentPage;
        var listBox = document.getElementById('idData');
        var pageBox = document.getElementById('barcon');
        var loading = false;
        if (!listBox || !pageBox) return;

        function clampPage(page) {
            page = parseInt(page, 10) || 1;
            return Math.max(1, Math.min(page, totalPage));
        }

        function pageLabel(page) {
            if (totalChapters < 1) return 'C.0 - C.0';
            var start = (page - 1) * pageSize + 1;
            var end = Math.min(page * pageSize, totalChapters);
            return 'C.' + start + ' - C.' + end;
        }

        function buildUrl(page) {
            var url = new URL(window.location.href);
            url.hash = '';
            url.searchParams.set('ajax', 'chapters');
            url.searchParams.set('page', page);
            url.searchParams.set('pageSize', pageSize);
            return url.toString();
        }

        function setSelectPage() {
            var select = document.getElementById('indexselect');
            if (select) select.value = currentPage;
        }

        function updateButtons() {
            var prev = document.getElementById('prevBtn');
            var next = document.getElementById('nextBtn');
            if (!prev || !next) return;
            prev.textContent = currentPage === 1 ? 'None' : 'Prev';
            prev.classList.toggle('none', currentPage === 1);
            next.textContent = currentPage === totalPage ? 'None' : 'Next';
            next.classList.toggle('none', currentPage === totalPage);
        }

        function generatePagination() {
            var html = '<a class="index-container-btn" href="javascript:;" data-page-action="first">First</a>' +
                '<a class="index-container-btn" id="prevBtn" href="javascript:;" data-page-action="prev">Prev</a>' +
                '<select id="indexselect">';
            for (var i = 1; i <= totalPage; i++) {
                html += '<option value="' + i + '">' + pageLabel(i) + '</option>';
            }
            html += '</select>' +
                '<a class="index-container-btn" id="nextBtn" href="javascript:;" data-page-action="next">Next</a>' +
                '<a class="index-container-btn" href="javascript:;" data-page-action="last">Last</a>';
            pageBox.innerHTML = html;
            setSelectPage();
            updateButtons();
        }

        function loadPage(page) {
            page = clampPage(page);
            if (loading || page === currentPage) {
                setSelectPage();
                return;
            }
            if (!window.fetch || !window.URL) {
                window.location.search = '?page=' + page;
                return;
            }

            loading = true;
            listBox.classList.add('loading');
            fetch(buildUrl(page), {headers: {'X-Requested-With': 'XMLHttpRequest'}})
                .then(function(response) {
                    if (!response.ok) throw new Error('request failed');
                    return response.json();
                })
                .then(function(data) {
                    if (!data || data.code !== 200) throw new Error('invalid response');
                    listBox.innerHTML = data.html || '';
                    currentPage = parseInt(data.page, 10) || page;
                    pageSize = parseInt(data.pageSize, 10) || pageSize;
                    totalPage = parseInt(data.totalPage, 10) || totalPage;
                    totalChapters = parseInt(data.totalChapters, 10) || totalChapters;
                    generatePagination();
                })
                .catch(function() {
                    setSelectPage();
                    alert('Chapter list loading failed, please try again.');
                })
                .then(function() {
                    loading = false;
                    listBox.classList.remove('loading');
                });
        }

        $(pageBox).on('change.indexList', '#indexselect', function() {
            loadPage(this.value);
        }).on('click.indexList', '[data-page-action]', function(e) {
            e.preventDefault();
            var action = this.getAttribute('data-page-action');
            if (action === 'first') loadPage(1);
            else if (action === 'prev') loadPage(currentPage - 1);
            else if (action === 'next') loadPage(currentPage + 1);
            else if (action === 'last') loadPage(totalPage);
        });

        currentPage = clampPage(currentPage);
        generatePagination();
    }

    initBookcase();
    initRating();
    initDescription();
    initPagination();
})(window, document, window.jQuery);

$(function() {
	$('.sidebar-link').click(
		function() {
			var url=$(this).attr('href');
			$('#content').load(url);
			return false;
		});
    
    
    $('#content').on('click', '.inner-link', function(event) {
   																	event.preventDefault();
    																	var url=$(this).attr('href');
    																	$('#content').load(url);
																	});
  
    
});